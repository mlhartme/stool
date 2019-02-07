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

import com.google.gson.JsonObject;
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
import net.oneandone.stool.scm.Scm;
import net.oneandone.stool.templates.TemplateField;
import net.oneandone.stool.templates.Tomcat;
import net.oneandone.stool.templates.Variable;
import net.oneandone.stool.util.Field;
import net.oneandone.stool.util.Info;
import net.oneandone.stool.util.LogEntry;
import net.oneandone.stool.util.Macros;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Property;
import net.oneandone.stool.util.Session;
import net.oneandone.stool.util.StandardProperty;
import net.oneandone.stool.util.TemplateProperty;
import net.oneandone.stool.util.Vhost;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.MultiWriter;
import net.oneandone.sushi.io.OS;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.repository.RepositoryPolicy;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import static net.oneandone.stool.stage.Project.State.UP;

/**
 * Concrete implementations are SourceProject or ArtifactProject.
 */
public abstract class Project {
    public static FileNode backstageDirectory(FileNode projectDirectory) {
        return projectDirectory.join(".backstage");
    }

    //--

    public static Project load(Session session, FileNode backstageLink) throws IOException {
        FileNode backstageResolved;

        try {
            backstageResolved = backstageLink.resolveLink();
        } catch (IOException e) {
            throw new IOException("unknown stage id: " + backstageLink.getName(), e);
        }
        return load(session, session.loadStageConfiguration(backstageResolved), backstageLink.getName(), backstageResolved.getParent());
    }

    private static Project load(Session session, StageConfiguration configuration, String id, FileNode directory) throws IOException {
        Project result;
        String origin;

        origin = probe(directory);
        if (origin == null) {
            throw new IOException("cannot determine stage origin: " + directory);
        }
        result = createOpt(session, id, origin, configuration, directory);
        if (result == null) {
            throw new IOException("unknown stage type: " + directory);
        }
        return result;
    }

    /** @return stage url or null if not a stage */
    public static String probe(FileNode directory) throws IOException {
        Node artifactGav;

        directory.checkDirectory();
        artifactGav = ArtifactProject.urlFile(directory);
        if (artifactGav.exists()) {
            return artifactGav.readString().trim();
        }
        return Scm.checkoutUrlOpt(directory);
    }

    public static Project createOpt(Session session, String id, String origin, StageConfiguration configuration, FileNode directory) throws IOException {
        if (configuration == null) {
            throw new IllegalArgumentException();
        }
        directory.checkDirectory();
        if (origin.startsWith("gav:") || origin.startsWith("file:")) {
            return new ArtifactProject(session, origin, id, directory, configuration);
        }
        if (directory.join(configuration.pom).exists()) {
            return SourceProject.forLocal(session, id, directory, configuration);
        }
        return null;
    }

    //--

    protected final String origin;

    public final Stage stage;

    /** user visible directory */
    protected FileNode directory;
    private Maven lazyMaven;

    //--

    public Project(Session session, String origin, String id, FileNode directory, StageConfiguration configuration) {
        this.origin = origin;
        this.stage = new Stage(session, id, backstageDirectory(directory), configuration);
        this.directory = directory;
    }

    public Stage getStage() {
        return stage;
    }
    public FileNode getDirectory() {
        return directory;
    }
    public String getOrigin() {
        return origin;
    }
    public String getType() {
        return getClass().getSimpleName().toLowerCase();
    }

    public String backstageLock() {
        return "backstage-" + stage.getId();
    }

    public String directoryLock() {
        return "directory-" + stage.getId();
    }

    public abstract boolean updateAvailable();

    /** @return login name */
    public String createdBy() throws IOException {
        return creatorFile().readString().trim();
    }

    private FileNode creatorFile() throws IOException {
        FileNode file;
        FileNode link;

        file = stage.directory.join("creator.touch");
        if (!file.exists()) {
            link = stage.session.backstageLink(stage.getId());
            file.getParent().mkdirOpt();
            file.writeString(link.getOwner().getName());
            file.setLastModified(Files.getLastModifiedTime(link.toPath(), LinkOption.NOFOLLOW_LINKS).toMillis());
        }
        return file;
    }

    public LocalDateTime created() throws IOException {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(creatorFile().getLastModified()), ZoneId.systemDefault());
    }

    //-- pid file handling

    public boolean isWorking() throws IOException {
        return stage.session.lockManager.hasExclusiveLocks(directoryLock(), backstageLock());
    }

    public State state() throws IOException {
        if (stage.session.bedroom.contains(stage.getId())) {
            return State.SLEEPING;
        } else if (stage.dockerContainer() != null) {
            return UP;
        } else {
            return State.DOWN;
        }

    }

    //--

    public abstract List<String> vhostNames() throws IOException;

    /** @return vhost to docroot mapping, where vhost does *not* include the stage name */
    public abstract Map<String, FileNode> vhosts() throws IOException;

    public Map<String, FileNode> selectedVhosts() throws IOException {
        Map<String, FileNode> vhosts;
        Iterator<Map.Entry<String, FileNode>> iter;
        List<String> selected;
        String vhostname;

        vhosts = vhosts();
        selected = stage.config().select;
        if (!selected.isEmpty()) {
            iter = vhosts.entrySet().iterator();
            while (iter.hasNext()) {
                vhostname = iter.next().getKey();
                if (!selected.contains(vhostname)) {
                    iter.remove();
                }
            }
        }
        return vhosts;
    }

    public Ports loadPortsOpt() throws IOException {
        return stage.session.pool().stageOpt(stage.getId());
    }

    /** @return empty list of no ports are allocated */
    public List<String> namedUrls() throws IOException {
        List<String> result;

        result = new ArrayList<>();
        for (Map.Entry<String, String> entry : urlMap().entrySet()) {
            result.add(entry.getKey() + " " + entry.getValue());
        }
        return result;
    }

    /** @return empty map of no ports are allocated */
    public Map<String, String> urlMap() throws IOException {
        Ports ports;

        ports = loadPortsOpt();
        return ports == null ? new HashMap<>() : ports.urlMap(stage.getName(), stage.session.configuration.hostname, stage.config().url);
    }

    /** @return nummer of applications */
    public abstract int size() throws IOException;

    public abstract String getDefaultBuildCommand();

    public void start(Console console, Ports ports, boolean noCache) throws Exception {
        Engine engine;
        String image;
        String container;
        Engine.Status status;
        String tag;
        FileNode context;
        Map<String, String> mounts;

        checkMemory();
        engine = stage.session.dockerEngine();
        tag = stage.getId();
        context = dockerContext(ports);
        wipeContainer(engine);
        console.verbose.println("building image ... ");
        try (Writer log = new FlushWriter(stage.directory.join("image.log").newWriter())) {
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
        mounts = bindMounts(ports, context.join(".source").exists(), isSystem());
        for (Map.Entry<String, String> entry : mounts.entrySet()) {
            console.verbose.println("  " + entry.getKey() + "\t -> " + entry.getValue());
        }
        container = engine.containerCreate(tag,  stage.getName() + "." + stage.session.configuration.hostname,
                OS.CURRENT == OS.MAC, 1024L * 1024 * stage.config().memory, null, null,
                Collections.emptyMap(), mounts, ports.dockerMap());
        console.verbose.println("created container " + container);
        engine.containerStart(container);
        status = engine.containerStatus(container);
        if (status != Engine.Status.RUNNING) {
            throw new IOException("unexpected status: " + status);
        }
        stage.dockerContainerFile().writeString(container);
    }

    /* TODO: work for tomcat only */
    public void awaitStartup(Console console) throws IOException, InterruptedException {
        Ports ports;
        String state;

        ports = loadPortsOpt();
        for (int count = 0; true; count++) {
            try {
                state = jmxEngineState(ports);
                break;
            } catch (Exception e) {
                if (count > 40) {
                    throw new IOException("initial state timed out: " + e.getMessage(), e);
                }
                Thread.sleep(50);
            }
        }
        for (int count = 1; !"STARTED".equals(state); count++) {
            if (count > 10 * 60 *5) {
                throw new IOException("tomcat startup timed out, state" + state);
            }
            if (count % 100 == 99) {
                console.info.println("waiting for tomcat startup ... " + state);
            }
            Thread.sleep(100);
            state = jmxEngineState(ports);
        }
    }

    private MBeanServerConnection lazyJmxConnection;

    private MBeanServerConnection jmxConnection(Ports ports) throws IOException {
        if (lazyJmxConnection == null) {
            JMXServiceURL url;

            // see https://docs.oracle.com/javase/tutorial/jmx/remote/custom.html
            try {
                url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + stage.session.configuration.hostname + ":" + ports.jmx() + "/jmxrmi");
            } catch (MalformedURLException e) {
                throw new IllegalStateException(e);
            }
            lazyJmxConnection = JMXConnectorFactory.connect(url, null).getMBeanServerConnection();
        }
        return lazyJmxConnection;
    }

    private String jmxEngineState(Ports ports) throws IOException {
        MBeanServerConnection connection;
        ObjectName name;

        connection = jmxConnection(ports);
        try {
            name = new ObjectName("Catalina:type=Engine");
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException(e);
        }
        try {
            return (String) connection.getAttribute(name, "stateName");
        } catch (ReflectionException | InstanceNotFoundException | AttributeNotFoundException | MBeanException e) {
            throw new IllegalStateException();
        }
    }


    public void wipeDocker(Engine engine) throws IOException {
        wipeContainer(engine);
        wipeImages(engine, null);
    }

    public void wipeImages(Engine engine, String keep) throws IOException {
        for (String image : engine.imageList(dockerLabel())) {
            if (!image.equals(keep)) {
                stage.session.console.verbose.println("remove image: " + image);
                engine.imageRemove(image);
            }
        }
    }

    public void wipeContainer(Engine engine) throws IOException {
        for (String image : engine.imageList(dockerLabel())) {
            for (String container : engine.containerList(image)) {
                stage.session.console.verbose.println("remove container: " + container);
                engine.containerRemove(container);
            }
        }
    }

    private Map<String, String> dockerLabel() {
        return Strings.toMap("stool", stage.getId());
    }

    public abstract List<String> faultProjects() throws IOException;

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

    // CAUTION: blocks until ctrl-c.
    // Format: https://docs.docker.com/engine/api/v1.33/#operation/ContainerAttach
    public void tailF(PrintWriter dest) throws IOException {
        Engine engine;

        engine = stage.session.dockerEngine();
        engine.containerLogsFollow(stage.dockerContainer(), new OutputStream() {
            @Override
            public void write(int b) {
                dest.write(b);
                if (b == 10) {
                    dest.flush(); // newline
                }
            }
        });
    }

    private Map<String, String>bindMounts(Ports ports, boolean source, boolean systemBinds) throws IOException {
        Map<String, String> result;
        List<FileNode> lst;
        Iterator<FileNode> iter;
        FileNode merged;

        result = new HashMap<>();
        result.put(stage.directory.join("logs").mkdirOpt().getAbsolute(), "/var/log/stool");
        if (source) {
            result.put(getDirectory().getAbsolute(), "/stage");
        } else {
            for (Vhost vhost : ports.vhosts()) {
                if (vhost.isWebapp()) {
                    if (vhost.isArtifact()) {
                        result.put(vhost.docroot.getParent().getAbsolute(), "/vhosts/" + vhost.name);
                    } else {
                        result.put(vhost.docroot.getAbsolute(), "/vhosts/" + vhost.name);
                    }
                }
            }
        }
        if (systemBinds) {
            result.put(stage.session.configuration.docker, stage.session.configuration.docker);

            lst = new ArrayList<>();
            lst.add(stage.session.home);  // for stool home
            if (!stage.session.configuration.systemExtras.isEmpty()) {
                lst.add(stage.session.world.file(stage.session.configuration.systemExtras));
            }
            lst.addAll(stage.session.stageDirectories());

            iter = lst.iterator();
            merged = iter.next();
            while (iter.hasNext()) {
                merged = merge(merged, iter.next());
            }
            add(result, merged);
            add(result, Main.stoolCp(stage.session.world).getParent()); // don't merge /usr/bin
            add(result, stage.session.world.getHome()); // for Maven credentials; don't merge /home with /opt stuff
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
        stage.session.console.verbose.println("merge " + left + " + " + right + " -> " + current);
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

        src = stage.config().template;
        dest = stage.directory.join("context");
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
        result.put("systemExtras", stage.session.configuration.systemExtras);
        result.put("hostHome", stage.session.world.getHome().getAbsolute());
        result.put("certname", stage.session.configuration.vhosts ? "*." + stage.getName() + "." + stage.session.configuration.hostname : stage.session.configuration.hostname);
        result.put("tomcat", new Tomcat(this, context, stage.session, ports));
        for (Variable env : environment) {
            value = stage.config().templateEnv.get(env.name);
            if (value == null) {
                throw new IOException("missing variable in template.env: " + env.name);
            }
            result.put(env.name, env.parse(value));
        }
        return result;
    }

    /** Fails if container is not running */
    public void stop(Console console) throws IOException {
        String container;
        Engine engine;

        container = stage.dockerContainer();
        if (container == null) {
            throw new IOException("container is not running.");
        }
        console.info.println("stopping container ...");
        engine = stage.session.dockerEngine();
        engine.containerStop(container, 300);
        stage.dockerContainerFile().deleteFile();
    }

    private void checkMemory() throws IOException {
        int requested;

        requested = stage.config().memory;
        int unreserved = stage.session.memUnreserved();
        if (requested > unreserved) {
            throw new ArgumentException("Cannot reserve memory:\n"
              + "  unreserved: " + unreserved + "\n"
              + "  requested: " + requested + "\n"
              + "Consider stopping stages.");
        }
    }

    //--

    public void move(FileNode newDirectory) throws IOException {
        FileNode link;

        link = stage.session.backstageLink(stage.getId());
        link.deleteTree();
        directory.move(newDirectory);
        directory = newDirectory;
        backstageDirectory(directory).link(link);
    }

    //--

    @Override
    public String toString() {
        return getType() + " " + origin;
    }

    //-- util

    public void checkNotUp() throws IOException {
        if (state() == UP) {
            throw new IOException("stage is not stopped.");
        }
    }

    public FileNode modifiedFile() throws IOException {
        FileNode file;

        file = stage.directory.join("modified.touch");
        if (!file.exists()) {
            file.getParent().mkdirOpt();
            file.writeString(stage.session.user);
        }
        return file;
    }

    public void modify() throws IOException {
        FileNode file;

        file = modifiedFile();
        file.getParent().mkdirOpt();
        file.writeString(stage.session.user);
    }

    public String lastModifiedBy() throws IOException {
        return modifiedFile().readString().trim();
    }

    public long lastModifiedAt() throws IOException {
        return modifiedFile().getLastModified();
    }

    /** @return launcher with build environment */
    public Launcher launcher(String... command) {
        return launcher(directory, command);
    }

    public Launcher launcher(FileNode working, String... command) {
        Launcher launcher;

        launcher = new Launcher(working, command);
        stage.session.environment(this).save(launcher);
        return launcher;
    }

    public abstract boolean refreshPending(Console console) throws IOException;

    public void restoreFromBackup(Console console) throws IOException {
        console.info.println("Nothing to restore.");
    }

    public void executeRefresh(Console console) throws IOException {
        launcher(Strings.toArray(Separator.SPACE.split(macros().replace(stage.config().refresh)))).exec(console.info);
    }

    //--

    public void tuneConfiguration() throws IOException {
        if (stage.config().memory == 0 || stage.config().memory == 400) {
            stage.config().memory = Math.min(4096, 200 + size() * stage.session.configuration.baseMemory);
        }
        if (stage.config().build.isEmpty() || stage.config().build.equals("false")) {
            stage.config().build = getDefaultBuildCommand();
        }
    }

    public void initialize() throws IOException {
        // important: this is the last step in stage creation; creating this file indicates that the stage is ready
        stage.session.saveStageProperties(stage.config(), stage.directory);
    }

    //--

    public void setMaven(Maven maven) {
        this.lazyMaven = maven;
    }

    /** CAUTION: this is not a session method, because it respects the stage repository */
    public Maven maven() throws IOException {
        World world;
        String mavenHome;
        FileNode settings;

        if (lazyMaven == null) {
            world = stage.session.world;
            mavenHome = stage.config().mavenHome();
            if (mavenHome == null) {
                settings = stage.session.home.join("maven-settings.xml");
            } else {
                settings = world.file(mavenHome).join("conf/settings.xml");
            }
            // CAUTION: shared plexus - otherwise, Maven components are created over and over again
            lazyMaven = Maven.withSettings(world, localRepository(), settings, null, stage.session.plexus(), null, null);
            // always get the latest snapshots
            lazyMaven.getRepositorySession().setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        }
        return lazyMaven;
    }

    protected List<MavenProject> loadWars(FileNode rootPom) throws IOException {
        List<MavenProject> wars;
        List<String> profiles;
        Properties userProperties;

        wars = new ArrayList<>();
        profiles = new ArrayList<>();
        userProperties = new Properties();
        addProfilesAndProperties(userProperties, profiles, stage.config().mavenOpts);
        addProfilesAndProperties(userProperties, profiles, getBuild());
        stage.session.console.verbose.println("profiles: " + profiles);
        stage.session.console.verbose.println("userProperties: " + userProperties);
        warProjects(rootPom, userProperties, profiles, wars);
        if (wars.size() == 0) {
            throw new IOException("no war projects");
        }
        return wars;
    }

    public String getBuild() {
        return macros().replace(stage.config().build);
    }

    private Macros lazyMacros = null;

    public Macros macros() {
        if (lazyMacros == null) {
            lazyMacros = new Macros();
            lazyMacros.addAll(stage.session.configuration.macros);
            lazyMacros.add("directory", getDirectory().getAbsolute());
            lazyMacros.add("localRepository", localRepository().getAbsolute());
            lazyMacros.add("svnCredentials", Separator.SPACE.join(stage.session.svnCredentials().svnArguments()));
            lazyMacros.add("stoolSvnCredentials", stage.session.svnCredentials().stoolSvnArguments());
        }
        return lazyMacros;
    }

    public boolean isCommitted() throws IOException {
        if (this instanceof ArtifactProject) {
            return true;
        }
        return stage.session.scm(getOrigin()).isCommitted(this);
    }

    private void addProfilesAndProperties(Properties userProperties, List<String> profiles, String args) {
        int idx;

        for (String part : Separator.SPACE.split(args)) {
            if (part.startsWith("-P")) {
                profiles.add(part.substring(2));
            }
            if (part.startsWith("-D")) {
                part = part.substring(2);
                idx = part.indexOf('=');
                if (idx == -1) {
                    userProperties.put(part, "");
                } else {
                    userProperties.put(part.substring(0, idx), part.substring(idx + 1));
                }
            }
        }
    }
    private void warProjects(FileNode pomXml, Properties userProperties, List<String> profiles,
      List<MavenProject> result) throws IOException {
        MavenProject root;
        FileNode modulePom;

        try {
            root = maven().loadPom(pomXml, false, userProperties, profiles, null);
        } catch (ProjectBuildingException | RepositoryException e) {
            throw new IOException("cannot parse " + pomXml + ": " + e.getMessage(), e);
        }
        stage.session.console.verbose.println("loading " + pomXml);
        if ("war".equals(root.getPackaging())) {
            result.add(root);
        } else {
            for (String module : root.getModules()) {
                modulePom = stage.session.world.file(root.getBasedir()).join(module);
                if (modulePom.isDirectory()) {
                    modulePom = modulePom.join("pom.xml");
                }
                warProjects(modulePom, userProperties, profiles, result);
            }
        }
    }

    public boolean isSystem() {
        return stage.session.home.join("system").equals(directory.getParent());
    }

    public FileNode localRepository() {
        return stage.session.configuration.shared ? stage.directory.join(".m2") : stage.session.world.getHome().join(".m2/repository");
    }

    public static String timespan(long since) {
        long diff;
        StringBuilder result;
        long hours;

        diff = System.currentTimeMillis() - since;
        diff /= 1000;
        hours = diff / 3600;
        if (hours >= 48) {
            return (hours / 24) + " days";
        } else {
            result = new StringBuilder();
            new Formatter(result).format("%d:%02d:%02d", hours, diff % 3600 / 60, diff % 60);
            return result.toString();
        }
    }

    public int diskUsed() throws IOException {
        return used(directory);
    }

    public int containerDiskUsed() throws IOException {
        String container;
        JsonObject obj;

        container = stage.dockerContainer();
        if (container == null) {
            return 0;
        }
        obj = stage.session.dockerEngine().containerInspect(container, true);
        // not SizeRootFs, that's the image size plus the rw layer
        return (int) (obj.get("SizeRw").getAsLong() / (1024 * 1024));
    }

    /** @return megabytes */
    private static int used(FileNode dir) throws IOException {
        Path path;

        path = dir.toPath();
        final AtomicLong size = new AtomicLong(0);
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                size.addAndGet(attrs.size());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
                // TODO: hard-wired fault dependency
                if (file.endsWith(".backstage/fault") && e instanceof java.nio.file.AccessDeniedException) {
                    return FileVisitResult.CONTINUE;
                } else {
                    throw e;
                }
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                if (e != null) {
                    throw e;
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return (int) (size.get() / (1024 * 1024));
    }

    public static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public abstract List<FileNode> artifacts() throws IOException;

    public String buildtime() throws IOException {
        Collection<FileNode> artifacts;
        long time;

        artifacts = artifacts();
        if (artifacts.isEmpty()) {
            return null;
        }
        time = Long.MIN_VALUE;
        for (FileNode a : artifacts) {
            time = Math.max(time, a.getLastModified());
        }
        return FMT.format(Instant.ofEpochMilli(time));
    }

    public enum State {
        DOWN, SLEEPING, UP, WORKING;

        public String toString() {
            return name().toLowerCase();
        }
    }

    //-- stage name

    /**
     * The stage name has to be a valid domain name because is used as part of the application url.
     * See http://tools.ietf.org/html/rfc1035 section 2.3.1.
     */
    public static void checkName(String name) {
        char c;

        if (name.isEmpty()) {
            throw new ArgumentException("empty stage name is not allowed");
        }
        if (name.length() > 30) {
            //ITCA does not accept too long commonNames
            throw new ArgumentException("Stage Name is too long. Please take a shorter one.");
        }
        if (!isLetter(name.charAt(0))) {
            throw new ArgumentException("stage name does not start with a letter");
        }
        for (int i = 1; i < name.length(); i++) {
            c = name.charAt(i);
            if (!isValidStageNameChar(c)) {
                throw new ArgumentException("stage name contains illegal character: " + c);
            }
        }
    }

    public static boolean isValidStageNameChar(char c) {
        return isLetter(c) || isDigit(c) || c == '-' || c == '.';
    }
    // cannot use Character.is... because we check ascii only
    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
    // cannot use Character.is... because we check ascii only
    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    public static String nameForUrl(String url) {
        if (url.startsWith("gav:")) {
            return nameForGavUrl(url);
        } else if (url.startsWith("file:")) {
            return nameForFileUrl(url);
        } else {
            return nameForSvnOrGitUrl(url);
        }
    }

    private static String nameForGavUrl(String url) {
        int end;
        int start;

        url = one(url);
        end = url.lastIndexOf(':');
        if (end == -1) {
            return "stage";
        }
        start = url.lastIndexOf(':', end - 1);
        if (start == -1) {
            return "stage";
        }
        return url.substring(start + 1, end);
    }

    private static String nameForFileUrl(String url) {
        int idx;

        url = one(url);
        idx = url.lastIndexOf('/');
        if (idx == -1) {
            return "idx";
        }
        url = url.substring(idx + 1);
        idx = url.lastIndexOf('.');
        if (idx == -1) {
            return url;
        } else {
            return url.substring(0, idx);
        }
    }

    private static String one(String url) {
        int end;

        end = url.lastIndexOf(',');
        if (end != -1) {
            url = url.substring(0, end);
        }
        end = url.lastIndexOf('=');
        if (end != -1) {
            url = url.substring(0, end);
        }
        return url;
    }

    private static String nameForSvnOrGitUrl(String url) {
        String result;
        int idx;

        result = Strings.removeRightOpt(url, "/");
        idx = result.indexOf(':');
        if (idx != -1) {
            // strip protocol - important vor gav stages
            result = result.substring(idx + 1);
        }
        result = Strings.removeRightOpt(result, "/trunk");
        idx = result.lastIndexOf('/');
        result = result.substring(idx + 1); // ok for -1
        result = Strings.removeRightOpt(result, ".git");
        return result.isEmpty() ? "stage" : result;
    }

    //--

    public void checkConstraints() throws IOException {
        int used;
        int quota;

        if (stage.config().expire.isExpired()) {
            throw new ArgumentException("Stage expired " + stage.config().expire + ". To start it, you have to adjust the 'expire' date.");
        }
        quota = stage.config().quota;
        used = diskUsed() + containerDiskUsed();
        if (used > quota) {
            throw new ArgumentException("Stage quota exceeded. Used: " + used + " mb  >  quota: " + quota + " mb.\n" +
                    "Consider running 'stool cleanup'.");
        }
    }

    //--

    public List<Field> fields() throws IOException {
        List<Field> fields;

        fields = new ArrayList<>();
        fields.add(new Field("id") {
            @Override
            public Object get() {
                return stage.getId();
            }
        });
        fields.add(new Field("selected") {
            @Override
            public Object get() throws IOException {
                return stage.session.isSelected(Project.this);
            }
        });
        fields.add(new Field("directory") {
            @Override
            public Object get() {
                return Project.this.directory.getAbsolute();
            }
        });
        fields.add(new Field("backstage") {
            @Override
            public Object get() {
                return Project.this.stage.directory.getAbsolute();
            }
        });
        fields.add(new Field("origin") {
            @Override
            public Object get() {
                return Project.this.getOrigin();
            }
        });
        fields.add(new Field("type") {
            @Override
            public Object get() {
                return Project.this.getType();
            }
        });
        fields.add(new Field("created-by") {
            @Override
            public Object get() throws IOException {
                return stage.session.users.checkedStatusByLogin(Project.this.createdBy());
            }

        });
        fields.add(new Field("created-at") {
            @Override
            public Object get() throws IOException {
                return LogEntry.FULL_FMT.format(Project.this.created());
            }

        });
        fields.add(new Field("last-modified-by") {
            @Override
            public Object get() throws IOException {
                return stage.session.users.checkedStatusByLogin(Project.this.lastModifiedBy());
            }
        });
        fields.add(new Field("last-modified-at") {
            @Override
            public Object get() throws IOException {
                return Project.timespan(Project.this.lastModifiedAt());
            }
        });
        fields.add(new Field("buildtime") {
            @Override
            public Object get() throws IOException {
                return Project.this.buildtime();
            }
        });
        fields.add(new Field("disk") {
            @Override
            public Object get() throws IOException {
                return Project.this.diskUsed();
            }
        });
        fields.add(new Field("container-disk") {
            @Override
            public Object get() throws IOException {
                return Project.this.containerDiskUsed();
            }
        });
        fields.add(new Field("state") {
            @Override
            public Object get() throws IOException {
                return Project.this.state().toString();
            }
        });
        fields.add(new Field("uptime") {
            @Override
            public Object get() throws IOException {
                String container;

                container = Project.this.stage.dockerContainer();
                return container == null ? null : timespan(Project.this.stage.session.dockerEngine().containerStartedAt(container));
            }
        });
        fields.add(new Field("cpu") {
            @Override
            public Object get() throws IOException {
                String container;
                Engine engine;
                Stats stats;

                container = Project.this.stage.dockerContainer();
                if (container == null) {
                    return null;
                }
                engine = Project.this.stage.session.dockerEngine();
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

                container = Project.this.stage.dockerContainer();
                if (container == null) {
                    return null;
                }
                engine = Project.this.stage.session.dockerEngine();
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
                return Project.this.stage.dockerContainer();
            }
        });
        fields.add(new Field("apps") {
            @Override
            public Object get() throws IOException {
                return Project.this.namedUrls();
            }
        });
        fields.addAll(TemplateField.scanTemplate(this, stage.config().template));
        return fields;
    }

    public Field fieldOpt(String str) throws IOException {
        for (Field f : fields()) {
            if (str.equals(f.name())) {
                return f;
            }
        }
        return null;
    }

    public List<Info> fieldsAndName() throws IOException {
        List<Info> result;

        result = new ArrayList<>();
        result.add(propertyOpt("name"));
        result.addAll(fields());
        return result;
    }

    //--

    public Info info(String str) throws IOException {
        Info result;
        List<String> lst;

        result = propertyOpt(str);
        if (result != null) {
            return result;
        }
        result = fieldOpt(str);
        if (result != null) {
            return result;
        }
        lst = new ArrayList<>();
        for (Field f : fields()) {
            lst.add(f.name());
        }
        for (Property p : properties()) {
            lst.add(p.name());
        }
        throw new ArgumentException(str + ": no such status field or property, choose one of " + lst);
    }

    //--

    public List<Property> properties() {
        List<Property> result;
        Map<String, String> env;
        String prefix;

        result = new ArrayList<>();
        for (Accessor type : stage.session.accessors().values()) {
            if (!type.name.equals("template.env")) {
                result.add(new StandardProperty(type, stage.config()));
            }
        }
        env = stage.config().templateEnv;
        prefix = stage.config().template.getName() + ".";
        for (String name : stage.config().templateEnv.keySet()) {
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

    public int contentHash() throws IOException {
        return ("StageInfo{"
                + "name='" + stage.config().name + '\''
                + ", id='" + stage.getId() + '\''
                + ", comment='" + stage.config().comment + '\''
                + ", origin='" + origin + '\''
                + ", urls=" + urlMap()
                + ", state=" + state()
                + ", displayState=" + displayState()
                + ", last-modified-by='" + lastModifiedBy() + '\''
                + ", updateAvailable=" + updateAvailable()
                + '}').hashCode();
    }

    //-- for dashboard

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

    public String sharedText() throws IOException {
        Map<String, String> urls;

        urls = urlMap();
        if (urls == null) {
            return "";
        }
        String content;
        StringBuilder stringBuilder;
        stringBuilder = new StringBuilder("Hi, \n");
        for (String url : urls.values()) {
            stringBuilder.append(url).append("\n");
        }

        content = URLEncoder.encode(stringBuilder.toString(), "UTF-8");
        content = content.replace("+", "%20").replaceAll("\\+", "%20")
                .replaceAll("\\%21", "!")
                .replaceAll("\\%27", "'")
                .replaceAll("\\%28", "(")
                .replaceAll("\\%29", ")")
                .replaceAll("\\%7E", "~");

        return content;
    }

}
