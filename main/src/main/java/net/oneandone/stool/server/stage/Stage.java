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
package net.oneandone.stool.server.stage;

import com.google.gson.JsonObject;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.stool.client.Project;
import net.oneandone.stool.common.Reference;
import net.oneandone.stool.common.State;
import net.oneandone.stool.server.configuration.Accessor;
import net.oneandone.stool.server.configuration.StageConfiguration;
import net.oneandone.stool.server.docker.BuildError;
import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.templates.Tomcat;
import net.oneandone.stool.server.templates.Variable;
import net.oneandone.stool.server.util.Field;
import net.oneandone.stool.server.util.Info;
import net.oneandone.stool.server.util.LogReader;
import net.oneandone.stool.server.util.Ports;
import net.oneandone.stool.server.util.Property;
import net.oneandone.stool.server.util.Session;
import net.oneandone.stool.server.util.StandardProperty;
import net.oneandone.stool.server.util.UrlPattern;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.MultiWriter;
import net.oneandone.sushi.io.OS;
import net.oneandone.sushi.util.Strings;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
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
import java.util.Properties;

/** Represents the former backstage directory. From a Docker perspective, a stage roughly represents a Repository */
public class Stage {
    public final Session session;
    public final Reference reference;
    private final FileNode directory;
    public final StageConfiguration configuration;

    public Stage(Session session, FileNode directory, StageConfiguration configuration) {
        this.session = session;
        this.reference = new Reference(directory.getName());
        this.directory = directory;
        this.configuration = configuration;
    }

    public FileNode getDirectory() {
        return directory;
    }

    public String getName() {
        return configuration.name;
    }

    //-- state

    public State state() throws IOException {
        if (dockerContainerList().isEmpty()) {
            return State.DOWN;
        } else {
            return State.UP;
        }
    }

    //-- fields and properties

    public Field fieldOpt(String str) {
        for (Field f : fields()) {
            if (str.equals(f.name())) {
                return f;
            }
        }
        return null;
    }

    public List<Info> fieldsAndName() {
        List<Info> result;

        result = new ArrayList<>();
        result.add(propertyOpt("name"));
        result.addAll(fields());
        return result;
    }

    public Info info(String str) {
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

        result = new ArrayList<>();
        for (Accessor type : session.accessors().values()) {
            result.add(new StandardProperty(type, configuration));
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

    public List<Field> fields() {
        List<Field> fields;

        fields = new ArrayList<>();
        fields.add(new Field("id") {
            @Override
            public Object get() {
                return reference.getId();
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
        fields.add(new Field("running") {
            @Override
            public Object get() throws IOException {
                Map<String, Current> map;
                List<String> result;

                map = currentMap();
                result = new ArrayList<>(map.keySet());
                Collections.sort(result);
                return result;
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
                return namedUrls(null);
            }
        });
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
        return LogReader.create(session.logging.directory().join(reference.getId()));
    }

    public Logs logs() {
        return new Logs(directory.join("logs"));
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
            for (String container : engine.containerListForImage(image).keySet()) {
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

    public static final String LABEL_HOST_PORT_PREFIX = LABEL_PREFIX + "host.port.";
    public static final String LABEL_CONTAINER_PORT_PREFIX = LABEL_PREFIX + "container.port.";

    public static final String LABEL_MOUNT_SECRETS_PREFIX = LABEL_PREFIX + "mount-secrets-";

    public static final String LABEL_STOOL = LABEL_PREFIX + "stool";
    public static final String LABEL_STAGE = LABEL_PREFIX + "stage";
    public static final String LABEL_APP = LABEL_PREFIX + "app";
    public static final String LABEL_COMMENT = LABEL_PREFIX + "comment";
    public static final String LABEL_ORIGIN = LABEL_PREFIX + "origin";
    public static final String LABEL_CREATED_BY = LABEL_PREFIX + "created-by";
    public static final String LABEL_CREATED_ON = LABEL_PREFIX + "created-on";

    private Map<String, String> stageLabel() {
        return Strings.toMap(LABEL_STOOL, session.configuration.id, LABEL_STAGE, reference.getId());
    }

    /** @param keep 0 to keep all */
    public String build(Project project, String app, FileNode war, Console console, String comment, String origin,
                      String createdBy, String createdOn, boolean noCache, int keep) throws Exception {
        Engine engine;
        String image;
        String tag;
        FileNode context;
        Map<String, String> labels;
        Properties appProperties;
        FileNode template;
        Collection<Variable> env;
        Map<String, Object> buildArgs;
        StringWriter output;

        checkMemory();
        engine = session.dockerEngine();
        if (keep > 0) {
            wipeOldImages(engine,keep - 1);
        }
        tag = reference.getId() + ":" + TAG_FORMAT.format(LocalDateTime.now());
        appProperties = properties(war);
        template = template(appProperties);
        env = Variable.scanTemplate(template).values();
        buildArgs = buildArgs(env, appProperties);
        context = dockerContext(project, app, war, template, buildArgs);
        labels = stageLabel();
        labels.put(LABEL_APP, app);
        labels.put(LABEL_COMMENT, comment);
        labels.put(LABEL_ORIGIN, origin);
        labels.put(LABEL_CREATED_BY, createdBy);
        labels.put(LABEL_CREATED_ON, createdOn);
        console.verbose.println("building image ... ");
        output = new StringWriter();
        try {
            // don't close the tee writer, it would close console output as well
            image = engine.imageBuild(tag, convert(buildArgs), labels, context, noCache, MultiWriter.createTeeWriter(output, console.verbose));
        } catch (BuildError e) {
            console.verbose.println("image build output");
            console.verbose.println(e.output);
            throw e;
        } finally {
            output.close();
        }
        console.verbose.println("image built: " + image);
        return output.toString();
    }

    private static Map<String, String> convert(Map<String, Object> in) {
        Map<String, String> out;

        out = new HashMap<>(in.size());
        for (Map.Entry<String, Object> entry : in.entrySet()) {
            out.put(entry.getKey(), entry.getValue().toString());
        }
        return out;
    }

    public void start(Console console, int http, int https, Map<String, String> environment, Map<String, Integer> selection) throws IOException {
        Engine engine;
        String container;
        Engine.Status status;
        Ports hostPorts;
        Map<FileNode, String> mounts;

        checkMemory();
        engine = session.dockerEngine();
        for (Image image : resolve(engine, selection)) {
            for (String old : engine.containerListForImage(image.id).keySet()) {
                engine.containerRemove(old);
            }
            console.verbose.println("environment: " + environment);
            console.info.println(image.app + ": starting container ... ");
            mounts = bindMounts(image);
            for (Map.Entry<FileNode, String> mount : mounts.entrySet()) {
                console.verbose.println("  " + mount.getKey().getAbsolute() + "\t -> " + mount.getValue());
            }
            hostPorts = session.pool().allocate(this, image.app, http, https);
            container = engine.containerCreate(image.id,  getName() + "." + session.configuration.hostname,
                    OS.CURRENT == OS.MAC /* TODO: why */, 1024L * 1024 * configuration.memory, null, null,
                    hostPorts.toHostLabels(), environment, mounts, image.ports.map(hostPorts));
            console.verbose.println("created container " + container);
            engine.containerStart(container);
            status = engine.containerStatus(container);
            if (status != Engine.Status.RUNNING) {
                throw new IOException("unexpected status: " + status);
            }
        }
    }

    private List<Image> resolve(Engine engine, Map<String, Integer> selectionOrig) throws IOException {
        Map<String, Integer> selection;
        Map<String, List<Image>> allImages;
        Collection<String> running;
        String app;
        int idx;
        List<Image> list;
        List<Image> result;

        allImages = images(engine);
        if (allImages.isEmpty()) {
            throw new IOException("no apps to start - did you build the stage?");
        }
        running = currentMap().keySet();
        if (selectionOrig.isEmpty()) {
            selection = new HashMap<>();
            for (String a : allImages.keySet()) {
                selection.put(a, 0);
            }
        } else {
            selection = selectionOrig;
        }
        result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : selection.entrySet()) {
            app = entry.getKey();
            if (running.contains(app)) {
                session.console.info.println("warning: app will not be started because it is already running: " + app);
            } else {
                idx = entry.getValue();
                list = allImages.get(app);
                if (list == null) {
                    throw new IOException("app not found: " + app);
                }
                if (idx < 0 || idx >= list.size()) {
                    throw new IOException(app + ": app index not found: " + idx);
                }
                result.add(list.get(idx));
            }
        }
        return result;
    }

    /** Fails if container is not running */
    public void stop(Console console, List<String> apps) throws IOException {
        Map<String, Current> currentMap;
        Engine engine;
        Map<String, String> containers;
        List<String> unknown;
        List<String> notRunning;

        unknown = new ArrayList<>(apps);
        unknown.removeAll(images(session.dockerEngine()).keySet());
        if (!unknown.isEmpty()) {
            throw new IOException("unknown app(s): " + unknown);
        }
        currentMap = currentMap();
        containers = new LinkedHashMap<>();
        notRunning = new ArrayList<>();
        for (Map.Entry<String, Current> current : currentMap.entrySet()) {
            if (apps.isEmpty() || apps.contains(current.getKey())) {
                containers.put(current.getKey(), current.getValue().container);
            } else {
                notRunning.add(current.getKey());
            }
        }
        if (!notRunning.isEmpty()) {
            console.info.println("warning: the following apps will not be stopped because they are not running: " + apps);
        }
        if (containers.isEmpty()) {
            throw new IOException("stage is already stopped");
        }
        for (Map.Entry<String, String> entry : containers.entrySet()) {
            console.info.println(entry.getKey() + ": stopping container ...");
            engine = session.dockerEngine();
            engine.containerStop(entry.getValue(), 300);
        }
    }

    private Map<FileNode, String> bindMounts(Image image) throws IOException {
        Map<FileNode, String> result;

        result = new HashMap<>();
        result.put(directory.join("logs").mkdirOpt(), "/var/log/stool");
        if (image.ports.https != -1) {
            result.put(session.certificate(session.configuration.vhosts ? image.app + "." + getName() + "." + session.configuration.hostname
                    : session.configuration.hostname), "/usr/local/tomcat/conf/tomcat.p12");
        }
        for (Map.Entry<String, String> entry : image.secrets.entrySet()) {
            result.put(session.world.file(session.configuration.secrets).join(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static final String FREEMARKER_EXT = ".fm";

    private FileNode dockerContext(Project project, String app, FileNode war, FileNode src, Map<String, Object> buildArgs)
            throws IOException, TemplateException {
        Configuration configuration;
        FileNode dest;
        FileNode destparent;
        FileNode destfile;
        Template template;
        StringWriter tmp;

        configuration = new Configuration(Configuration.VERSION_2_3_26);
        configuration.setDefaultEncoding("UTF-8");

        dest = createContext();
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
                    template.process(templateEnv(app, project.getDirectory(), war, dest, buildArgs), tmp);
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

    private Properties properties(FileNode war) throws IOException {
        Node<?> root;

        root = war.openZip();
        return root.join("WEB-INF/classes/META-INF/stool.properties").readProperties();
    }

    private FileNode template(Properties appProperies) throws IOException {
        String template;

        template = appProperies.getProperty("template");
        if (template == null) {
            throw new IOException("missing propertyl: template");
        }
        return session.templates().join(template).checkDirectory();
    }

    private Map<String, Object> templateEnv(String app, FileNode project, FileNode war, FileNode context, Map<String, Object> buildArgs) {
        Map<String, Object> result;

        result = new HashMap<>();
        result.put("tomcat", new Tomcat(app, project, war,this, context, session));
        result.putAll(buildArgs);
        return result;
    }

    private Map<String, Object> buildArgs(Collection<Variable> environment, Properties appProperties) {
        Map<String, Object> result;
        String value;

        result = new HashMap<>();
        for (Variable env : environment) {
            value = appProperties.getProperty(env.name);
            if (value == null) {
                result.put(env.name, env.dflt);
            } else {
                result.put(env.name, env.parse(value));
            }
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

    /** maps app to its ports; empty map if not ports allocated yet */
    public Map<String, Ports> loadPorts() throws IOException {
        return session.pool().stage(reference.getId());
    }

    /**
     * @param oneApp null for all apps
     * @return empty map if no ports are allocated
     */
    public Map<String, String> urlMap(String oneApp) throws IOException {
        Map<String, String> result;
        String app;
        Ports ports;

        result = new LinkedHashMap<>();
        for (Map.Entry<String, Ports> entry : loadPorts().entrySet()) {
            app = entry.getKey();
            if (oneApp == null || oneApp.equals(app)) {
                ports = entry.getValue();
                result.putAll(UrlPattern.parse(configuration.url).urlMap(app, getName(), session.configuration.hostname, ports.http, ports.https));
            }
        }
        return result;
    }

    /** @return empty list of no ports are allocated */
    public List<String> namedUrls(String oneApp) throws IOException {
        List<String> result;

        result = new ArrayList<>();
        for (Map.Entry<String, String> entry : urlMap(oneApp).entrySet()) {
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
        return new ArrayList<>(engine.containerListRunning(LABEL_STAGE, reference.getId()).keySet());
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
                + ", id='" + reference.getId() + '\''
                + ", comment='" + configuration.comment + '\''
                // TODO: current immage, container?
                + ", urls=" + urlMap(null)
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

        urls = urlMap(null);
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

    private JMXConnector jmxConnection(Ports ports) throws IOException {
        JMXServiceURL url;

        // see https://docs.oracle.com/javase/tutorial/jmx/remote/custom.html
        try {
            url = new JMXServiceURL("service:jmx:jmxmp://" + session.configuration.hostname + ":" + ports.jmxmp);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        return JMXConnectorFactory.connect(url, null);
    }

    private String jmxEngineState(Ports ports) throws IOException {
        ObjectName name;

        try {
            name = new ObjectName("Catalina:type=Engine");
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException(e);
        }
        try (JMXConnector connection = jmxConnection(ports)) {
            return (String) connection.getMBeanServerConnection().getAttribute(name, "stateName");
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

    //--

    public FileNode createContext() throws IOException {
        FileNode result;

        result = directory.join("context");
        result.deleteTreeOpt();
        result.mkdir();
        return result;
    }
}
