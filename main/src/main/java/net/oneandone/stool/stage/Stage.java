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
import net.oneandone.stool.configuration.Accessor;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.docker.BuildError;
import net.oneandone.stool.docker.Engine;
import net.oneandone.stool.docker.Stats;
import net.oneandone.stool.templates.TemplateField;
import net.oneandone.stool.templates.Tomcat;
import net.oneandone.stool.templates.Variable;
import net.oneandone.stool.util.Field;
import net.oneandone.stool.util.Info;
import net.oneandone.stool.util.LogEntry;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Property;
import net.oneandone.stool.util.Session;
import net.oneandone.stool.util.StandardProperty;
import net.oneandone.stool.util.TemplateProperty;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.MultiWriter;
import net.oneandone.sushi.io.OS;
import net.oneandone.sushi.util.Strings;

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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Represents the former backstage directory. From a Docker perspective, a stage roughly represents a Repository */
public class Stage {
    public enum State {
        DOWN, UP, WORKING;

        public String toString() {
            return name().toLowerCase();
        }
    }

    public final Session session;
    public final FileNode directory;
    private final StageConfiguration configuration;

    public Stage(Session session, FileNode directory, StageConfiguration configuration) {
        this.session = session;
        this.directory = directory;
        this.configuration = configuration;
    }

    public String getId() {
        return directory.getName();
    }

    public StageConfiguration config() {
        return configuration;
    }

    public String getName() {
        return config().name;
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

    //-- pid file handling


    public String lock() {
        return "stage-" + getId();
    }

    public boolean isWorking() throws IOException {
        return session.lockManager.hasExclusiveLocks(lock());
    }

    public State state() throws IOException {
        if (dockerContainer() != null) {
            return State.UP;
        } else {
            return State.DOWN;
        }

    }

    public void checkNotUp() throws IOException {
        if (state() == State.UP) {
            throw new IOException("stage is not stopped.");
        }
    }

    public String displayState() throws IOException {
        switch (isWorking() ? State.WORKING : state()) {
            case UP:
                return "success";
            case WORKING:
                return "primary";
            default:
                return "danger";
        }
    }

    public void saveConfig() throws IOException {
        session.saveStageConfiguration(config(), directory);
    }

    //--

    public List<Field> fields() throws IOException {
        List<Field> fields;

        fields = new ArrayList<>();
        fields.add(new Field("id") {
            @Override
            public Object get() {
                return getId();
            }
        });
        fields.add(new Field("selected") {
            @Override
            public Object get() throws IOException {
                return session.isSelected(Stage.this);
            }
        });
        fields.add(new Field("stage") {
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
        fields.add(new Field("created-by") {
            @Override
            public Object get() throws IOException {
                return session.users.checkedStatusByLogin(createdBy());
            }

        });
        fields.add(new Field("created-at") {
            @Override
            public Object get() throws IOException {
                return LogEntry.FULL_FMT.format(created());
            }

        });
        fields.add(new Field("last-modified-by") {
            @Override
            public Object get() throws IOException {
                return session.users.checkedStatusByLogin(lastModifiedBy());
            }
        });
        fields.add(new Field("last-modified-at") {
            @Override
            public Object get() throws IOException {
                return timespan(lastModifiedAt());
            }
        });
        fields.add(new Field("origin") {
            @Override
            public Object get() throws IOException {
                return origin();
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
        fields.add(new Field("images") {
            @Override
            public List<String> get() throws IOException {
                Engine engine;
                List<Image> images;
                List<String> result;

                engine = session.dockerEngine();
                images = images(engine);
                result = new ArrayList<>();
                for (Image image : images) {
                    result.add(image.toString());
                }
                return result;
            }
        });
        fields.add(new Field("container") {
            @Override
            public Object get() throws IOException {
                return dockerContainer();
            }
        });
        fields.add(new Field("apps") {
            @Override
            public Object get() throws IOException {
                return namedUrls();
            }
        });
        fields.add(new Field("disk") {
            @Override
            public Object get() throws IOException {
                return diskUsed();
            }
        });
        fields.add(new Field("uptime") {
            @Override
            public Object get() throws IOException {
                String container;

                container = dockerContainer();
                return container == null ? null : timespan(session.dockerEngine().containerStartedAt(container));
            }
        });
        fields.addAll(TemplateField.scanTemplate(this, config().template));
        return fields;
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

    //--

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
        wipeImages(engine);
    }

    public void wipeImages(Engine engine) throws IOException {
        for (String image : engine.imageList(dockerLabel())) {
            session.console.verbose.println("remove image: " + image);
            engine.imageRemove(image);
        }
    }

    /** @return sorted list */
    public List<Image> images(Engine engine) throws IOException {
        List<Image> result;

        result = new ArrayList<>();
        for (String image : engine.imageList(dockerLabel())) {
            result.add(Image.load(engine, image));
        }
        Collections.sort(result);
        return result;
    }

    public void wipeOldImages(Engine engine, int keep) throws IOException {
        List<Image> images;
        String remove;

        images = images(engine);
        if (images.size() <= keep) {
            return;
        }
        while (images.size() > keep) {
            remove = images.remove(0).id;
            session.console.verbose.println("remove image: " + remove);
            engine.imageRemove(remove);
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

    public void checkConstraints() throws IOException {
        int used;
        int quota;

        if (config().expire.isExpired()) {
            throw new ArgumentException("Stage expired " + config().expire + ". To start it, you have to adjust the 'expire' date.");
        }
        quota = config().quota;
        used = diskUsed();
        if (used > quota) {
            throw new ArgumentException("Stage quota exceeded. Used: " + used + " mb  >  quota: " + quota + " mb.\n" +
                    "Consider running 'stool cleanup'.");
        }
    }

    private Map<String, String> dockerLabel() {
        return Strings.toMap("stool", getId());
    }

    private static final DateTimeFormatter TAG_FORMAT = DateTimeFormatter.ofPattern("yyMMdd-HHmmss");

    public static final String LABEL_PORTS = "ports";
    public static final String LABEL_COMMENT = "comment";
    public static final String LABEL_ORIGIN = "origin";
    public static final String LABEL_CREATED_BY = "created-by";
    public static final String LABEL_CREATED_ON = "created-on";

    /** @param keep 0 to keep all */
    public void build(Map<String, FileNode> wars, Console console, Ports ports,
                      String comment, String origin, String createdBy, String createdOn,
                      boolean noCache, int keep) throws Exception {
        Engine engine;
        String image;
        String tag;
        FileNode context;
        Map<String, String> label;

        checkMemory();
        engine = session.dockerEngine();
        if (keep > 0) {
            wipeOldImages(engine,keep - 1);
        }
        tag = getId() + ":" + TAG_FORMAT.format(LocalDateTime.now());
        context = dockerContext(wars, ports);
        wipeContainer(engine);
        label = dockerLabel();
        label.put(LABEL_PORTS, toString(ports.dockerMap()));
        label.put(LABEL_COMMENT, comment);
        label.put(LABEL_ORIGIN, origin);
        label.put(LABEL_CREATED_BY, createdBy);
        label.put(LABEL_CREATED_ON, createdOn);
        console.verbose.println("building image ... ");
        try (Writer log = new FlushWriter(directory.join("image.log").newWriter())) {
            // don't close the tee writer, it would close console output as well
            image = engine.imageBuild(tag, label, context, noCache, MultiWriter.createTeeWriter(log, console.verbose));
        } catch (BuildError e) {
            console.verbose.println("image build output");
            console.verbose.println(e.output);
            throw e;
        }
        console.verbose.println("image built: " + image);
    }

    private static String toString(Map<Integer, Integer> map) {
        boolean first;
        StringBuilder result;

        first = true;
        result = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            if (first) {
                first = false;
            } else {
                result.append(',');
            }
            result.append(entry.getKey());
            result.append(',');
            result.append(entry.getValue());
        }
        return result.toString();
    }

    public void start(Console console, int idx) throws Exception {
        Engine engine;
        List<Image> images;
        Image image;
        String container;
        Engine.Status status;
        Map<String, String> mounts;

        checkMemory();
        engine = session.dockerEngine();
        images = images(engine);
        if (idx < 0 || idx >= images.size()) {
            throw new IOException("image not found: " + idx);
        }
        image = images.get(idx);
        console.info.println("starting container ...");
        mounts = bindMounts();
        for (Map.Entry<String, String> entry : mounts.entrySet()) {
            console.verbose.println("  " + entry.getKey() + "\t -> " + entry.getValue());
        }
        container = engine.containerCreate(image.id,  getName() + "." + session.configuration.hostname,
                OS.CURRENT == OS.MAC, 1024L * 1024 * config().memory, null, null,
                Collections.emptyMap(), mounts, image.ports);
        console.verbose.println("created container " + container);
        engine.containerStart(container);
        status = engine.containerStatus(container);
        if (status != Engine.Status.RUNNING) {
            throw new IOException("unexpected status: " + status);
        }
        dockerContainerFile().writeString(container);
    }

    /** Fails if container is not running */
    public void stop(Console console) throws IOException {
        String container;
        Engine engine;

        container = dockerContainer();
        if (container == null) {
            throw new IOException("container is not running.");
        }
        console.info.println("stopping container ...");
        engine = session.dockerEngine();
        engine.containerStop(container, 300);
        dockerContainerFile().deleteFile();
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

    private Map<String, String> bindMounts() throws IOException {
        Map<String, String> result;

        result = new HashMap<>();
        result.put(directory.join("logs").mkdirOpt().getAbsolute(), "/var/log/stool");
        return result;
    }

    private static final String FREEMARKER_EXT = ".fm";

    private FileNode dockerContext(Map<String, FileNode> wars, Ports ports) throws IOException, TemplateException {
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
                    template.process(templateEnv(wars, dest, ports, environment), tmp);
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

    private Map<String, Object> templateEnv(Map<String, FileNode> wars, FileNode context, Ports ports, Collection<Variable> environment) throws IOException {
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
        result.put("hostHome", session.world.getHome().getAbsolute());
        result.put("certname", session.configuration.vhosts ? "*." + getName() + "." + session.configuration.hostname : session.configuration.hostname);
        result.put("tomcat", new Tomcat(wars,this, context, session, ports));
        for (Variable env : environment) {
            value = config().templateEnv.get(env.name);
            if (value == null) {
                throw new IOException("missing variable in template.env: " + env.name);
            }
            result.put(env.name, env.parse(value));
        }
        return result;
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

    //--

    /** @return login name */
    public String createdBy() throws IOException {
        return creatorFile().readString().trim();
    }

    public FileNode creatorFile() {
        return directory.join("creator.touch");
    }

    public LocalDateTime created() throws IOException {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(creatorFile().getLastModified()), ZoneId.systemDefault());
    }

    public FileNode modifiedFile() throws IOException {
        FileNode file;

        file = directory.join("modified.touch");
        if (!file.exists()) {
            file.getParent().mkdirOpt();
            file.writeString(session.user);
        }
        return file;
    }

    public void modify() throws IOException {
        FileNode file;

        file = modifiedFile();
        file.getParent().mkdirOpt();
        file.writeString(session.user);
    }

    public String lastModifiedBy() throws IOException {
        return modifiedFile().readString().trim();
    }

    public long lastModifiedAt() throws IOException {
        return modifiedFile().getLastModified();
    }

    //--

    public Ports loadPortsOpt() throws IOException {
        return session.pool().stageOpt(getId());
    }

    /** @return empty map of no ports are allocated */
    public Map<String, String> urlMap() throws IOException {
        Ports ports;

        ports = loadPortsOpt();
        return ports == null ? new HashMap<>() : ports.urlMap(getName(), session.configuration.hostname, config().url);
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

    //--

    private Image currentImage() throws IOException {
        Engine engine;
        String container;
        List<Image> images;
        JsonObject json;

        engine = session.dockerEngine();
        container = dockerContainer();
        if (container != null) {
            json = engine.containerInspect(container, false);
            return Image.load(engine, json.get("Image").getAsString());
        } else {
            images = images(session.dockerEngine());
            return images.isEmpty() ? null : images.get(0);
        }
    }

    public String origin() throws IOException {
        Image image;

        image = currentImage();
        return image == null ? null : image.origin;
    }

    public int contentHash() throws IOException {
        return ("StageInfo{"
                + "name='" + config().name + '\''
                + ", id='" + getId() + '\''
                + ", comment='" + config().comment + '\''
                + ", origin='" + origin() + '\''
                + ", urls=" + urlMap()
                + ", state=" + state()
                + ", displayState=" + displayState()
                + ", last-modified-by='" + lastModifiedBy() + '\''
                + '}').hashCode();
    }

    //-- for dashboard

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

    //--
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
                if (count > 600) {
                    throw new IOException("initial state timed out: " + e.getMessage(), e);
                }
                if (count % 100 == 99) {
                    console.info.println("waiting for tomcat startup ... ");
                }
                Thread.sleep(100);
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
                url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + session.configuration.hostname + ":" + ports.jmx() + "/jmxrmi");
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

    public int diskUsed() throws IOException {
        String container;
        JsonObject obj;

        container = dockerContainer();
        if (container == null) {
            return 0;
        }
        obj = session.dockerEngine().containerInspect(container, true);
        // not SizeRootFs, that's the image size plus the rw layer
        return (int) (obj.get("SizeRw").getAsLong() / (1024 * 1024));
    }

    // CAUTION: blocks until ctrl-c.
    // Format: https://docs.docker.com/engine/api/v1.33/#operation/ContainerAttach
    public void tailF(PrintWriter dest) throws IOException {
        Engine engine;

        engine = session.dockerEngine();
        engine.containerLogsFollow(dockerContainer(), new OutputStream() {
            @Override
            public void write(int b) {
                dest.write(b);
                if (b == 10) {
                    dest.flush(); // newline
                }
            }
        });
    }

}
