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
import net.oneandone.stool.templates.TemplateField;
import net.oneandone.stool.templates.Tomcat;
import net.oneandone.stool.templates.Variable;
import net.oneandone.stool.util.Field;
import net.oneandone.stool.util.FlushWriter;
import net.oneandone.stool.util.Info;
import net.oneandone.stool.util.LogReader;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Property;
import net.oneandone.stool.util.Session;
import net.oneandone.stool.util.StandardProperty;
import net.oneandone.stool.util.TemplateProperty;
import net.oneandone.stool.util.UrlPattern;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Represents the former backstage directory. From a Docker perspective, a stage roughly represents a Repository */
public class Stage {
    public final Session session;
    public final FileNode directory;
    public final StageConfiguration configuration;

    public Stage(Session session, FileNode directory, StageConfiguration configuration) {
        this.session = session;
        this.directory = directory;
        this.configuration = configuration;
    }

    public String getId() {
        return directory.getName();
    }

    public String getName() {
        return configuration.name;
    }

    //-- state

    public enum State {
        DOWN("primary"), UP("success"), WORKING("danger");

        public String display;

        State(String display) {
            this.display = display;
        }

        public String toString() {
            return name().toLowerCase();
        }
    }

    public State state() throws IOException {
        if (session.lockManager.hasExclusiveLocks(lock())) {
            return State.WORKING;
        } else if (dockerContainerList() != null) {
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

    public String lock() {
        return "stage-" + getId();
    }

    //-- fields and properties

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
                return logReader().first().dateTime;
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
                return timespan(logReader().lastModified());
            }
        });
        fields.add(new Field("apps") {
            @Override
            public Object get() throws IOException {
                return namedUrls();
            }
        });
        fields.addAll(TemplateField.scanTemplate(this, configuration.template));
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

    public void saveConfig() throws IOException {
        configuration.save(session.gson, StageConfiguration.file(directory));
    }

    //-- logs

    public LogReader logReader() throws IOException {
        return LogReader.create(session.logging.directory().join(getId()));
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

    //-- docker

    public void wipeDocker(Engine engine) throws IOException {
        wipeContainer(engine);
        wipeImages(engine);
    }

    public void wipeImages(Engine engine) throws IOException {
        for (String image : engine.imageList(stageLabel())) {
            session.console.verbose.println("remove image: " + image);
            engine.imageRemove(image, true /* because the might be multiple tags */);
        }
    }

    /** @return app mapped to sorted list */
    public Map<String, List<Image>> images(Engine engine) throws IOException {
        Map<String, List<Image>> result;
        Image image;
        List<Image> list;

        result = new HashMap<>();
        for (String id : engine.imageList(stageLabel())) {
            image = Image.load(engine, id);
            list = result.get(image.app);
            if (list == null) {
                list = new ArrayList<>();
                result.put(image.app, list);
            }
            list.add(image);
        }
        for (List<Image> l : result.values()) {
            Collections.sort(l);
        }
        return result;
    }

    public void wipeOldImages(Engine engine, int keep) throws IOException {
        Map<String, List<Image>> allImages;
        List<Image> images;
        String remove;

        allImages = images(engine);
        for (String app : allImages.keySet()) {
            images = allImages.get(app);
            if (images.size() > keep) {
                while (images.size() > keep) {
                    remove = images.remove(images.size() - 1).id;
                    session.console.verbose.println("remove image: " + remove);
                    engine.imageRemove(remove, true); // TODO: 'force' could remove an image even if there's still a container running; but I need force to delete with multiple tags ...
                }
            }
        }
    }

    public void wipeContainer(Engine engine) throws IOException {
        for (String image : engine.imageList(stageLabel())) {
            for (String container : engine.containerList(image)) {
                session.console.verbose.println("remove container: " + container);
                engine.containerRemove(container);
            }
        }
    }

    public void checkConstraints() throws IOException {
        int used;
        int quota;

        if (configuration.expire.isExpired()) {
            throw new ArgumentException("Stage expired " + configuration.expire + ". To start it, you have to adjust the 'expire' date.");
        }
        quota = configuration.quota;
        /* TODO: used = diskUsed();
        if (used > quota) {
            throw new ArgumentException("Stage quota exceeded. Used: " + used + " mb  >  quota: " + quota + " mb.\n" +
                    "Consider running 'stool cleanup'.");
        }*/
    }

    private static final DateTimeFormatter TAG_FORMAT = DateTimeFormatter.ofPattern("yyMMdd-HHmmss");

    private static final String LABEL_PREFIX = "net.onetandone.stool-";

    public static final String LABEL_PORT_HTTP = LABEL_PREFIX + "port.http";
    public static final String LABEL_PORT_HTTPS = LABEL_PREFIX + "port.https";
    public static final String LABEL_PORT_JMXMP = LABEL_PREFIX + "port.jmxmp";
    public static final String LABEL_PORT_DEBUG = LABEL_PREFIX + "port.debug";

    public static final String LABEL_STAGE = LABEL_PREFIX + "stage";
    public static final String LABEL_APP = LABEL_PREFIX + "app";
    public static final String LABEL_COMMENT = LABEL_PREFIX + "comment";
    public static final String LABEL_ORIGIN = LABEL_PREFIX + "origin";
    public static final String LABEL_CREATED_BY = LABEL_PREFIX + "created-by";
    public static final String LABEL_CREATED_ON = LABEL_PREFIX + "created-on";

    private Map<String, String> stageLabel() {
        return Strings.toMap(LABEL_STAGE, getId());
    }

    /** @param keep 0 to keep all */
    public void build(String app, FileNode war, Console console, String comment, String origin, String createdBy, String createdOn,
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
        context = dockerContext(app, war);
        label = stageLabel();
        label.put(LABEL_APP, app);
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

    public void start(Console console, int idx) throws Exception {
        Engine engine;
        Map<String, List<Image>> allImages;
        List<Image> images;
        Image image;
        String container;
        Engine.Status status;
        Map<String, String> mounts;

        checkMemory();
        engine = session.dockerEngine();
        wipeContainer(engine);
        allImages = images(engine);
        for (String app : allImages.keySet()) {
            images = allImages.get(app);
            if (idx < 0 || idx >= images.size()) {
                console.info.printf("%s: image %d not found - using %d\n", app, idx, images.size() - 1);
                image = images.get(images.size() - 1);
            } else {
                image = images.get(idx);
            }
            console.info.println("starting container ...");
            mounts = bindMounts();
            for (Map.Entry<String, String> entry : mounts.entrySet()) {
                console.verbose.println("  " + entry.getKey() + "\t -> " + entry.getValue());
            }
            container = engine.containerCreate(image.id,  getName() + "." + session.configuration.hostname,
                    OS.CURRENT == OS.MAC, 1024L * 1024 * configuration.memory, null, null,
                    Collections.emptyMap(), mounts, map(image.ports, image.app));
            console.verbose.println("created container " + container);
            engine.containerStart(container);
            status = engine.containerStatus(container);
            if (status != Engine.Status.RUNNING) {
                throw new IOException("unexpected status: " + status);
            }
        }
    }

    private Map<Integer, Integer> map(Ports containerPorts, String app) throws IOException {
        Ports hostPorts;
        Map<Integer, Integer> result;

        hostPorts = session.pool().allocate(this, app, null);
        result = new HashMap<>();
        addOpt(result, containerPorts.http, hostPorts.http);
        addOpt(result, containerPorts.https, hostPorts.https);
        addOpt(result, containerPorts.jmxmp, hostPorts.jmxmp);
        addOpt(result, containerPorts.debug, hostPorts.debug);
        return result;
    }

    private static void addOpt(Map<Integer, Integer> dest, int left, int right) {
        if (right == -1) {
            throw new IllegalStateException();
        }
        if (left != -1) {
            dest.put(left, right);
        }
    }

    /** Fails if container is not running */
    public void stop(Console console) throws IOException {
        List<String> containerList;
        Engine engine;

        containerList = dockerContainerList();
        if (containerList == null) {
            throw new IOException("container is not running.");
        }
        for (String container : containerList) {
            console.info.println("stopping container ...");
            engine = session.dockerEngine();
            engine.containerStop(container, 300);
        }
    }

    private Map<String, String> bindMounts() throws IOException {
        Map<String, String> result;

        result = new HashMap<>();
        result.put(directory.join("logs").mkdirOpt().getAbsolute(), "/var/log/stool");
        return result;
    }

    private static final String FREEMARKER_EXT = ".fm";

    private FileNode dockerContext(String app, FileNode war) throws IOException, TemplateException {
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

        src = this.configuration.template;
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
                    template.process(templateEnv(app, war, dest, environment), tmp);
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

    private Map<String, Object> templateEnv(String app, FileNode war, FileNode context, Collection<Variable> environment) throws IOException {
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
        result.put("tomcat", new Tomcat(app, war,this, context, session));
        for (Variable env : environment) {
            value = configuration.templateEnv.get(env.name);
            if (value == null) {
                throw new IOException("missing variable in template.env: " + env.name);
            }
            result.put(env.name, env.parse(value));
        }
        return result;
    }

    private void checkMemory() throws IOException {
        int requested;

        requested = configuration.memory;
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
        return logReader().first().user;
    }

    //--

    public Ports loadPortsFirst() throws IOException { // TODO
        Map<String, Ports> map;

        map = loadPorts();
        if (map.isEmpty()) {
            return null;
        } else {
            return map.values().iterator().next();
        }
    }

    /** maps app to its ports; empty map if not ports allocated yet */
    public Map<String, Ports> loadPorts() throws IOException {
        return session.pool().stage(getId());
    }

    /** @return empty map if no ports are allocated */
    public Map<String, String> urlMap() throws IOException {
        Map<String, String> result;
        String app;
        Ports ports;

        result = new LinkedHashMap<>();
        for (Map.Entry<String, Ports> entry : loadPorts().entrySet()) {
            app = entry.getKey();
            ports = entry.getValue();
            result.putAll(UrlPattern.parse(configuration.url).urlMap(app, getName(), session.configuration.hostname, ports.http, ports.https));
        }
        return result;
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

    public static class Current {
        public final Image image;
        public final String container;

        public Current(Image image, String container) {
            this.image = image;
            this.container = container;
        }
    }

    public List<String> dockerContainerList() throws IOException {
        Engine engine;

        engine = session.dockerEngine();
        return engine.containerList(LABEL_STAGE, getId());
    }

    public Map<String, Current> currentMap() throws IOException {
        Engine engine;
        List<String> containerList;
        JsonObject json;
        Map<String, Current> result;
        Image image;

        engine = session.dockerEngine();
        result = new HashMap<>();
        containerList = dockerContainerList();
        for (String container : containerList) {
            json = engine.containerInspect(container, false);
            image = Image.load(engine, Strings.removeLeft(json.get("Image").getAsString(), "sha256:"));
            result.put(image.app, new Current(image, container));
        }
        return result;
    }

    public int contentHash() throws IOException {
        return ("StageInfo{"
                + "name='" + configuration.name + '\''
                + ", id='" + getId() + '\''
                + ", comment='" + configuration.comment + '\''
                // TODO: current immage, container?
                + ", urls=" + urlMap()
                + ", state=" + state()
                + ", displayState=" + state().display
                + '}').hashCode();
    }

    public String lastModifiedBy() throws IOException {
        return logReader().prev().user;
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

    public void awaitStartup(Console console) throws IOException, InterruptedException {
        String app;
        Ports ports;
        String state;

        for (Map.Entry<String, Ports> entry : loadPorts().entrySet()) {
            app = entry.getKey();
            ports = entry.getValue();
            for (int count = 0; true; count++) {
                try {
                    state = jmxEngineState(ports);
                    break;
                } catch (Exception e) {
                    if (count > 600) {
                        throw new IOException(app + ": initial state timed out: " + e.getMessage(), e);
                    }
                    if (count % 100 == 99) {
                        console.info.println(app + ": waiting for tomcat startup ... ");
                    }
                    Thread.sleep(100);
                }
            }
            for (int count = 1; !"STARTED".equals(state); count++) {
                if (count > 10 * 60 * 5) {
                    throw new IOException(app + ": tomcat startup timed out, state" + state);
                }
                if (count % 100 == 99) {
                    console.info.println(app + ": waiting for tomcat startup ... " + state);
                }
                Thread.sleep(100);
                state = jmxEngineState(ports);
            }
        }
    }

    private MBeanServerConnection lazyJmxConnection;

    private MBeanServerConnection jmxConnection(Ports ports) throws IOException {
        if (lazyJmxConnection == null) {
            JMXServiceURL url;

            // see https://docs.oracle.com/javase/tutorial/jmx/remote/custom.html
            try {
                url = new JMXServiceURL("service:jmx:jmxmp://" + session.configuration.hostname + ":" + ports.jmxmp);
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

    // CAUTION: blocks until ctrl-c.
    // Format: https://docs.docker.com/engine/api/v1.33/#operation/ContainerAttach
    public void tailF(PrintWriter dest) throws IOException {
        List<String> containers;
        Engine engine;

        containers = dockerContainerList();
        if (containers.size() != 1) {
            session.console.info.println("ignoring -tail option because container is not unique");
        } else {
            engine = session.dockerEngine();
            engine.containerLogsFollow(containers.get(0), new OutputStream() {
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

}
