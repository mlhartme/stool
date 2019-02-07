/*
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.stool.stage;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.maven.embedded.Maven;
import net.oneandone.stool.cli.Main;
import net.oneandone.stool.configuration.Accessor;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.docker.BuildError;
import net.oneandone.stool.docker.Engine;
import net.oneandone.stool.docker.Stats;
import net.oneandone.stool.templates.Tomcat;
import net.oneandone.stool.templates.Variable;
import net.oneandone.stool.util.Field;
import net.oneandone.stool.util.Macros;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Property;
import net.oneandone.stool.util.Session;
import net.oneandone.stool.util.StandardProperty;
import net.oneandone.stool.util.TemplateProperty;
import net.oneandone.stool.util.Vhost;
import net.oneandone.sushi.fs.DeleteException;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.NodeNotFoundException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.MultiWriter;
import net.oneandone.sushi.io.OS;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import org.eclipse.aether.repository.RepositoryPolicy;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static net.oneandone.stool.stage.Project.State.UP;

/** represents the former backstage directory */
public class Stage {
    public final Session session;
    private final String id;
    public final FileNode directory;
    private final StageConfiguration configuration;
    private Maven lazyMaven;

    public Stage(Session session, String id, FileNode directory, StageConfiguration configuration) {
        this.session = session;
        this.id = id;
        this.directory = directory;
        this.configuration = configuration;
        this.lazyMaven = null;
    }

    public String getId() {
        return id;
    }

    public StageConfiguration config() {
        return configuration;
    }

    public String getName() {
        return config().name;
    }

    //-- pid file handling


    public String backstageLock() {
        return "backstage-" + id;
    }

    public String directoryLock() {
        return "directory-" + id;
    }

    public boolean isWorking() throws IOException {
        return session.lockManager.hasExclusiveLocks(directoryLock(), backstageLock());
    }

    public Project.State state() throws IOException {
        if (session.bedroom.contains(id)) {
            return Project.State.SLEEPING;
        } else if (dockerContainer() != null) {
            return UP;
        } else {
            return Project.State.DOWN;
        }

    }

    public void checkNotUp() throws IOException {
        if (state() == UP) {
            throw new IOException("stage is not stopped.");
        }
    }

    public String displayState() throws IOException {
        switch (isWorking() ? Project.State.WORKING : state()) {
            case UP:
                return "success";
            case WORKING:
                return "primary";
            default:
                return "danger";
        }
    }

    //--

    public List<Field> fields() throws IOException {
        List<Field> fields;

        fields = new ArrayList<>();
        fields.add(new Field("id") {
            @Override
            public Object get() {
                return id;
            }
        });
        fields.add(new Field("backstage") {
            @Override
            public Object get() {
                return directory.getAbsolute();
            }
        });
        fields.add(new Field("state") {
            @Override
            public Object get() throws IOException {
                return state().toString();
            }
        });
        fields.add(new Field("cpu") {
            @Override
            public Object get() throws IOException {
                String container;
                Engine engine;
                Stats stats;

                container = dockerContainer();
                if (container == null) {
                    return null;
                }
                engine = session.dockerEngine();
                stats = engine.containerStats(container);
                if (stats != null) {
                    return stats.cpu;
                } else {
                    // not started
                    return 0;
                }
            }
        });
        fields.add(new Field("mem") {
            @Override
            public Object get() throws IOException {
                String container;
                Engine engine;
                Stats stats;

                container = dockerContainer();
                if (container == null) {
                    return null;
                }
                engine = session.dockerEngine();
                stats = engine.containerStats(container);
                if (stats != null) {
                    return stats.memoryUsage * 100 / stats.memoryLimit;
                } else {
                    // not started
                    return 0;
                }
            }
        });
        fields.add(new Field("container") {
            @Override
            public Object get() throws IOException {
                return dockerContainer();
            }
        });
        return fields;
    }

    public List<Property> properties() {
        List<Property> result;
        Map<String, String> env;
        String prefix;

        result = new ArrayList<>();
        for (Accessor type : session.accessors().values()) {
            if (!type.name.equals("template.env")) {
                result.add(new StandardProperty(type, configuration));
            }
        }
        env = configuration.templateEnv;
        prefix = configuration.template.getName() + ".";
        for (String name : configuration.templateEnv.keySet()) {
            result.add(new TemplateProperty(prefix + name, env, name));
        }
        return result;
    }

    public Property propertyOpt(String name) {
        for (Property property : properties()) {
            if (name.equals(property.name())) {
                return property;
            }
        }
        return null;
    }

    //-- Maven stuff

    public void setMaven(Maven maven) {
        this.lazyMaven = maven;
    }

    /** CAUTION: this is not a session method, because it respects the stage repository */
    public Maven maven() throws IOException {
        World world;
        String mavenHome;
        FileNode settings;

        if (lazyMaven == null) {
            world = session.world;
            mavenHome = configuration.mavenHome();
            if (mavenHome == null) {
                settings = session.home.join("maven-settings.xml");
            } else {
                settings = world.file(mavenHome).join("conf/settings.xml");
            }
            // CAUTION: shared plexus - otherwise, Maven components are created over and over again
            lazyMaven = Maven.withSettings(world, localRepository(), settings, null, session.plexus(), null, null);
            // always get the latest snapshots
            lazyMaven.getRepositorySession().setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        }
        return lazyMaven;
    }


    public FileNode localRepository() {
        return session.configuration.shared ? directory.join(".m2") : session.world.getHome().join(".m2/repository");
    }

    public void cleanupMavenRepository(Console console) throws NodeNotFoundException, DeleteException {
        FileNode repository;

        repository = directory.join(".m2");
        if (repository.exists()) {
            console.info.println("Removing Maven Repository at " + repository.getAbsolute());
            repository.deleteTree();
        } else {
            console.verbose.println("No Maven Repository found at " + repository.getAbsolute());
        }
    }

    public Logs logs() {
        return new Logs(directory.join("logs"));
    }

    public void rotateLogs(Console console) throws IOException {
        Node archived;

        for (Node logfile : directory.find("**/*.log")) {
            archived = archiveDirectory(logfile).join(logfile.getName() + ".gz");
            console.verbose.println(String.format("rotating %s to %s", logfile.getRelative(directory), archived.getRelative(directory)));
            logfile.gzip(archived);
            logfile.deleteFile();
        }
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private Node archiveDirectory(Node node) throws MkdirException {
        return node.getParent().join("archive", FMT.format(LocalDateTime.now())).mkdirsOpt();
    }

    //--

    public FileNode dockerContainerFile() {
        return directory.join("container.id");
    }

    public String dockerContainer() throws IOException {
        FileNode file;

        file = dockerContainerFile();
        return file.exists() ? file.readString().trim() : null;
    }

    public void wipeDocker(Engine engine) throws IOException {
        wipeContainer(engine);
        wipeImages(engine, null);
    }

    public void wipeImages(Engine engine, String keep) throws IOException {
        for (String image : engine.imageList(dockerLabel())) {
            if (!image.equals(keep)) {
                session.console.verbose.println("remove image: " + image);
                engine.imageRemove(image);
            }
        }
    }

    public void wipeContainer(Engine engine) throws IOException {
        for (String image : engine.imageList(dockerLabel())) {
            for (String container : engine.containerList(image)) {
                session.console.verbose.println("remove container: " + container);
                engine.containerRemove(container);
            }
        }
    }

    private Map<String, String> dockerLabel() {
        return Strings.toMap("stool", id);
    }

    public void start(Console console, Ports ports, boolean noCache) throws Exception {
        Engine engine;
        String image;
        String container;
        Engine.Status status;
        String tag;
        FileNode context;
        Map<String, String> mounts;

        checkMemory();
        engine = session.dockerEngine();
        tag = id;
        context = dockerContext(ports);
        wipeContainer(engine);
        console.verbose.println("building image ... ");
        try (Writer log = new FlushWriter(directory.join("image.log").newWriter())) {
            // don't close the tee writer, it would close console output as well
            image = engine.imageBuild(tag, dockerLabel(), context, noCache, MultiWriter.createTeeWriter(log, console.verbose));
        } catch (BuildError e) {
            console.verbose.println("image build output");
            console.verbose.println(e.output);
            throw e;
        }
        console.verbose.println("image built: " + image);
        wipeImages(engine, image);
        console.info.println("starting container ...");
        mounts = bindMounts(ports, isSystem());
        for (Map.Entry<String, String> entry : mounts.entrySet()) {
            console.verbose.println("  " + entry.getKey() + "\t -> " + entry.getValue());
        }
        container = engine.containerCreate(tag,  getName() + "." + session.configuration.hostname,
                OS.CURRENT == OS.MAC, 1024L * 1024 * config().memory, null, null,
                Collections.emptyMap(), mounts, ports.dockerMap());
        console.verbose.println("created container " + container);
        engine.containerStart(container);
        status = engine.containerStatus(container);
        if (status != Engine.Status.RUNNING) {
            throw new IOException("unexpected status: " + status);
        }
        dockerContainerFile().writeString(container);
    }

    private static class FlushWriter extends Writer {
        private final Writer dest;

        private FlushWriter(Writer dest) {
            this.dest = dest;
        }


        @Override
        public void write(char[] chars, int ofs, int len) throws IOException {
            int c;

            for (int i = 0; i < len; i++) {
                c = chars[ofs + i];
                dest.write(c);
                if (c == '\n') {
                    flush();
                }
            }
        }

        @Override
        public void flush() throws IOException {
            dest.flush();
        }

        @Override
        public void close() throws IOException {
            dest.close();
        }
    }

    private Map<String, String> bindMounts(Ports ports, boolean systemBinds) throws IOException {
        Map<String, String> result;
        List<FileNode> lst;
        Iterator<FileNode> iter;
        FileNode merged;

        result = new HashMap<>();
        result.put(directory.join("logs").mkdirOpt().getAbsolute(), "/var/log/stool");
        for (Vhost vhost : ports.vhosts()) {
            if (vhost.isWebapp()) {
                if (vhost.isArtifact()) {
                    result.put(vhost.docroot.getParent().getAbsolute(), "/vhosts/" + vhost.name);
                } else {
                    result.put(vhost.docroot.getAbsolute(), "/vhosts/" + vhost.name);
                }
            }
        }
        if (systemBinds) {
            result.put(session.configuration.docker, session.configuration.docker);

            lst = new ArrayList<>();
            lst.add(session.home);  // for stool home
            if (!session.configuration.systemExtras.isEmpty()) {
                lst.add(session.world.file(session.configuration.systemExtras));
            }
            lst.addAll(session.stageDirectories());

            iter = lst.iterator();
            merged = iter.next();
            while (iter.hasNext()) {
                merged = merge(merged, iter.next());
            }
            add(result, merged);
            add(result, Main.stoolCp(session.world).getParent()); // don't merge /usr/bin
            add(result, session.world.getHome()); // for Maven credentials; don't merge /home with /opt stuff
        }
        return result;
    }

    private static void add(Map<String, String> result, FileNode path) {
        String str;

        str = path.getAbsolute();
        result.put(str, str);
    }

    private FileNode merge(FileNode left, FileNode right) {
        FileNode current;

        current = right;
        while (!left.hasAncestor(current)) {
            current = current.getParent();
        }
        session.console.verbose.println("merge " + left + " + " + right + " -> " + current);
        return current;
    }

    private static final String FREEMARKER_EXT = ".fm";

    private FileNode dockerContext(Ports ports) throws IOException, TemplateException {
        Configuration configuration;
        FileNode src;
        FileNode dest;
        FileNode destparent;
        FileNode destfile;
        Template template;
        StringWriter tmp;
        Collection<Variable> environment;

        configuration = new Configuration(Configuration.VERSION_2_3_26);
        configuration.setDefaultEncoding("UTF-8");

        src = config().template;
        dest = directory.join("context");
        dest.deleteTreeOpt();
        dest.mkdir();
        environment = Variable.scanTemplate(src).values();
        try {
            for (FileNode srcfile : src.find("**/*")) {
                if (srcfile.isDirectory()) {
                    continue;
                }
                destfile = dest.join(srcfile.getRelative(src));
                destparent = destfile.getParent();
                destparent.mkdirsOpt();
                if (destfile.getName().endsWith(FREEMARKER_EXT)) {
                    configuration.setDirectoryForTemplateLoading(srcfile.getParent().toPath().toFile());
                    template = configuration.getTemplate(srcfile.getName());
                    tmp = new StringWriter();
                    template.process(templateEnv(dest, ports, environment), tmp);
                    destfile = destparent.join(Strings.removeRight(destfile.getName(), FREEMARKER_EXT));
                    destfile.writeString(tmp.getBuffer().toString());
                } else {
                    srcfile.copy(destfile);
                }
            }
        } catch (IOException | TemplateException | RuntimeException | Error e) {
            // generate all or nothing
            try {
                dest.deleteTreeOpt();
            } catch (IOException nested) {
                e.addSuppressed(nested);
            }
            throw e;
        }
        return dest;
    }

    private Map<String, Object> templateEnv(FileNode context, Ports ports, Collection<Variable> environment) throws IOException {
        Map<String, Object> result;
        String value;

        result = new HashMap<>();

        if (OS.CURRENT == OS.MAC) {
            result.put("UID", "0");
            result.put("GID", "0");
        } else {
            result.put("UID", Long.toString(Engine.geteuid()));
            result.put("GID", Long.toString(Engine.getegid()));
        }
        result.put("system", isSystem());
        result.put("systemExtras", session.configuration.systemExtras);
        result.put("hostHome", session.world.getHome().getAbsolute());
        result.put("certname", session.configuration.vhosts ? "*." + getName() + "." + session.configuration.hostname : session.configuration.hostname);
        result.put("tomcat", new Tomcat(this, context, session, ports));
        for (Variable env : environment) {
            value = config().templateEnv.get(env.name);
            if (value == null) {
                throw new IOException("missing variable in template.env: " + env.name);
            }
            result.put(env.name, env.parse(value));
        }
        return result;
    }

    private Macros lazyMacros;

    public Macros macros() {
        if (lazyMacros == null) {
            lazyMacros = new Macros();
            lazyMacros.addAll(session.configuration.macros);
            // TODO lazyMacros.add("directory", getDirectory().getAbsolute());
            lazyMacros.add("localRepository", localRepository().getAbsolute());
            lazyMacros.add("svnCredentials", Separator.SPACE.join(session.svnCredentials().svnArguments()));
            lazyMacros.add("stoolSvnCredentials", session.svnCredentials().stoolSvnArguments());
        }
        return lazyMacros;
    }

    public boolean isSystem() {
        return session.home.join("system").equals(directory.getParent());
    }

    private void checkMemory() throws IOException {
        int requested;

        requested = config().memory;
        int unreserved = session.memUnreserved();
        if (requested > unreserved) {
            throw new ArgumentException("Cannot reserve memory:\n"
                    + "  unreserved: " + unreserved + "\n"
                    + "  requested: " + requested + "\n"
                    + "Consider stopping stages.");
        }
    }
}
