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
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.common.Reference;
import net.oneandone.stool.common.State;
import net.oneandone.stool.server.configuration.Accessor;
import net.oneandone.stool.server.configuration.StageConfiguration;
import net.oneandone.stool.server.docker.BuildError;
import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.templates.Variable;
import net.oneandone.stool.server.util.Field;
import net.oneandone.stool.server.util.Info;
import net.oneandone.stool.server.util.LogReader;
import net.oneandone.stool.server.util.Ports;
import net.oneandone.stool.server.util.Property;
import net.oneandone.stool.server.util.Session;
import net.oneandone.stool.server.util.StandardProperty;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
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
    private static final DateTimeFormatter TAG_FORMAT = DateTimeFormatter.ofPattern("yyMMdd-HHmmss");

    private static final String LABEL_PREFIX = "net.oneandone.stool-";

    public static final String IMAGE_LABEL_PORT_DECLARED_PREFIX = LABEL_PREFIX + "port.declared.";
    public static final String IMAGE_LABEL_MEMORY = LABEL_PREFIX + "memory";
    public static final String IMAGE_LABEL_URL_CONTEXT = LABEL_PREFIX + "url.context";
    public static final String IMAGE_LABEL_URL_SUFFIXES = LABEL_PREFIX + "url.suffixes";
    public static final String IMAGE_LABEL_FAULT = LABEL_PREFIX + "fault";
    public static final String IMAGE_LABEL_COMMENT = LABEL_PREFIX + "comment";
    public static final String IMAGE_LABEL_ORIGIN = LABEL_PREFIX + "origin";
    public static final String IMAGE_LABEL_CREATED_BY = LABEL_PREFIX + "created-by";
    public static final String IMAGE_LABEL_CREATED_ON = LABEL_PREFIX + "created-on";

    public static final String CONTAINER_LABEL_STOOL = LABEL_PREFIX + "stool";
    public static final String CONTAINER_LABEL_STAGE = LABEL_PREFIX + "stage";
    public static final String CONTAINER_LABEL_APP = LABEL_PREFIX + "app";
    public static final String CONTAINER_LABEL_PORT_USED_PREFIX = LABEL_PREFIX + "port.used.";


    //--

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
        return reference.getName();
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
        fields.add(new Field("name") {
            @Override
            public Object get() {
                return reference.getName();
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
        return LogReader.create(session.logging.directory().join(reference.getName()));
    }

    public Logs logs() {
        return new Logs(directory.join("logs"));
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private Node archiveDirectory(Node node) throws MkdirException {
        return node.getParent().join("archive", FMT.format(LocalDateTime.now())).mkdirsOpt();
    }

    //-- docker

    private List<String> listImages(Engine engine) throws IOException {
        Engine.ImageListInfo info;
        String tag;
        List<String> result;

        result = new ArrayList<>();
        for (Map.Entry<String, Engine.ImageListInfo> entry : engine.imageList().entrySet()) {
            info = entry.getValue();
            if (info.tags.size() == 1) {
                tag = info.tags.get(0);
                if (tag.startsWith(session.configuration.registryNamespace + "/" + reference.getName() + "/")) {
                    result.add(entry.getKey());
                }
            }
        }
        return result;
    }

    public void wipeDocker(Engine engine) throws IOException {
        wipeContainer(engine);
        wipeImages(engine);
    }

    public void wipeImages(Engine engine) throws IOException {
        for (String image : listImages(engine)) {
            session.logging.verbose("remove image: " + image);
            engine.imageRemove(image, true /* because the might be multiple tags */);
        }
    }

    /** @return app mapped to sorted list */
    public Map<String, List<Image>> images(Engine engine) throws IOException {
        Map<String, List<Image>> result;
        Image image;
        List<Image> list;

        result = new HashMap<>();
        for (String id :listImages(engine)) {
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
                    session.logging.verbose("remove image: " + remove);
                    engine.imageRemove(remove, true); // TODO: 'force' could remove an image even if there's still a container running; but I need force to delete with multiple tags ...
                }
            }
        }
    }

    public void wipeContainer(Engine engine) throws IOException {
        for (String image : listImages(engine)) {
            for (String container : engine.containerListForImage(image).keySet()) {
                session.logging.verbose("remove container: " + container);
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

    /** @param keep 0 to keep all */
    public String build(String app, FileNode war, String comment, String origin,
                        String createdBy, String createdOn, boolean noCache, int keep,
                        Map<String, String> arguments) throws Exception {
        Engine engine;
        String image;
        String tag;
        FileNode context;
        Map<String, String> labels;
        Properties appProperties;
        FileNode template;
        Collection<Variable> env;
        Map<String, String> buildArgs;
        StringWriter output;
        String result;

        engine = session.dockerEngine();
        if (keep > 0) {
            wipeOldImages(engine,keep - 1);
        }
        tag = session.configuration.registryNamespace + "/" + reference.getName() + "/" + app + ":" + TAG_FORMAT.format(LocalDateTime.now());
        appProperties = properties(war);
        template = template(appProperties);
        env = Variable.scanTemplate(template).values();
        buildArgs = buildArgs(env, appProperties);
        context = dockerContext(app, war, template);
        labels = new HashMap<>();
        labels.put(IMAGE_LABEL_COMMENT, comment);
        labels.put(IMAGE_LABEL_ORIGIN, origin);
        labels.put(IMAGE_LABEL_CREATED_BY, createdBy);
        labels.put(IMAGE_LABEL_CREATED_ON, createdOn);
        session.logging.verbose("building image ... ");
        output = new StringWriter();
        try {
            image = engine.imageBuild(tag, buildArgs, labels, context, noCache, output);
        } catch (BuildError e) {
            session.logging.verbose("image build output");
            session.logging.verbose(e.output);
            throw e;
        } finally {
            output.close();
        }
        result = output.toString();
        session.logging.verbose("successfully built image: " + image);
        session.logging.verbose(result);
        return result;
    }

    private static Map<String, String> convert(Map<String, Object> in) {
        Map<String, String> out;

        out = new HashMap<>(in.size());
        for (Map.Entry<String, Object> entry : in.entrySet()) {
            out.put(entry.getKey(), entry.getValue().toString());
        }
        return out;
    }

    public void start(int http, int https, Map<String, String> environment, Map<String, Integer> selection) throws IOException {
        Engine engine;
        String container;
        Engine.Status status;
        Ports hostPorts;
        Map<FileNode, String> mounts;
        Map<String, String> labels;

        engine = session.dockerEngine();


        int unreserved = session.memUnreserved();

        for (Image image : resolve(engine, selection)) {
            if (image.memory > unreserved) {
                throw new ArgumentException("Cannot reserve memory for app " + image.app + " :\n"
                        + "  unreserved: " + unreserved + "\n"
                        + "  requested: " + image.memory + "\n"
                        + "Consider stopping stages.");
            }
            for (String old : engine.containerListForImage(image.id).keySet()) {
                engine.containerRemove(old);
            }
            session.logging.verbose("environment: " + environment);
            session.logging.info(image.app + ": starting container ... ");
            mounts = bindMounts(image);
            for (Map.Entry<FileNode, String> mount : mounts.entrySet()) {
                session.logging.verbose("  " + mount.getKey().getAbsolute() + "\t -> " + mount.getValue());
            }
            hostPorts = session.pool().allocate(this, image.app, http, https);
            labels = hostPorts.toUsedLabels();
            labels.put(CONTAINER_LABEL_STOOL, session.configuration.registryNamespace);
            labels.put(CONTAINER_LABEL_APP, image.app);
            labels.put(CONTAINER_LABEL_STAGE, reference.getName());
            container = engine.containerCreate(image.id,  getName() + "." + session.configuration.hostname,
                    OS.CURRENT == OS.MAC /* TODO: why */, 1024L * 1024 * image.memory, null, null,
                    labels, environment, mounts, image.ports.map(hostPorts));
            session.logging.verbose("created container " + container);
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
                session.logging.info("warning: app will not be started because it is already running: " + app);
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
    public void stop(List<String> apps) throws IOException {
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
            session.logging.info("warning: the following apps will not be stopped because they are not running: " + apps);
        }
        if (containers.isEmpty()) {
            throw new IOException("stage is already stopped");
        }
        for (Map.Entry<String, String> entry : containers.entrySet()) {
            session.logging.info(entry.getKey() + ": stopping container ...");
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
        for (String project : image.faultProjects) { // TODO: authorization
            result.put(session.world.file(session.configuration.secrets).join(project), project);
        }
        return result;
    }

    private FileNode dockerContext(String app, FileNode war, FileNode src) throws IOException {
        FileNode context;
        FileNode destparent;
        FileNode destfile;

        context = createContext(app, war);
        try {
            for (FileNode srcfile : src.find("**/*")) {
                if (srcfile.isDirectory()) {
                    continue;
                }
                destfile = context.join(srcfile.getRelative(src));
                destparent = destfile.getParent();
                destparent.mkdirsOpt();
                srcfile.copy(destfile);
            }
        } catch (IOException | RuntimeException | Error e) {
            // generate all or nothing
            try {
                context.deleteTreeOpt();
            } catch (IOException nested) {
                e.addSuppressed(nested);
            }
            throw e;
        }
        return context;
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

    private Map<String, String> buildArgs(Collection<Variable> environment, Properties appProperties) {
        Map<String, String> result;
        String value;

        result = new HashMap<>();
        for (Variable env : environment) {
            value = appProperties.getProperty(env.name);
            if (value == null) {
                result.put(env.name, env.dflt);
            } else {
                result.put(env.name, value);
            }
        }
        return result;
    }

    //--

    /** @return login name */
    public String createdBy() throws IOException {
        return logReader().first().user;
    }

    //--

    /** maps app to its ports; empty map if not ports allocated yet */
    public Map<String, Ports> loadPorts() throws IOException {
        return session.pool().stage(reference.getName());
    }

    /**
     * @param oneApp null for all apps
     * @return empty map if no ports are allocated
     */
    public Map<String, String> urlMap(String oneApp) throws IOException {
        Engine engine;
        Map<String, String> result;
        String app;
        Map<String, Image> images;

        result = new LinkedHashMap<>();
        engine = session.dockerEngine();
        images = new HashMap<>();
        for (Engine.ContainerListInfo info : engine.containerList(Stage.CONTAINER_LABEL_STOOL).values()) {
            if (reference.getName().equals(info.labels.get(Stage.CONTAINER_LABEL_STAGE))) {
                images.put(info.labels.get(Stage.CONTAINER_LABEL_APP), Image.load(engine, info.imageId));
            }
        }
        for (Map.Entry<String, Ports> entry : loadPorts().entrySet()) {
            app = entry.getKey();
            if (oneApp == null || oneApp.equals(app)) {
                addUrlMap(images.get(app), app, entry.getValue(), result);
            }
        }
        return result;
    }

    private void addUrlMap(Image image, String app, Ports ports, Map<String, String> dest) {
        if (image == null) {
            throw new IllegalStateException("no image for app " + app);
        }
        if (ports.http != -1) {
            addNamed(app, url(image,"http", ports.http), dest);
        }
        if (ports.https != -1) {
            addNamed(app + " SSL", url(image,"https", ports.https), dest);
        }
    }

    private void addNamed(String name, List<String> urls, Map<String, String> dest) {
        int count;

        if (urls.size() == 1) {
            dest.put(name, urls.get(0));
        } else {
            count = 1;
            for (String url : urls) {
                dest.put(name + "_" + count, url);
                count++;
            }
        }
    }

    private List<String> url(Image image, String protocol, int port) {
        String hostname;
        String url;
        List<String> result;

        hostname = session.configuration.hostname;
        if (session.configuration.vhosts) {
            hostname = image.app + "." + getName() + "." + hostname;
        }
        url = protocol + "://" + hostname + ":" + port + "/" + image.urlContext;
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        result = new ArrayList<>();
        for (String suffix : image.urlSuffixes) {
            result.add(url + suffix);
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
        return new ArrayList<>(engine.containerListRunning(CONTAINER_LABEL_STAGE, reference.getName()).keySet());
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
                + "name='" + reference.getName() + '\''
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

    public void awaitStartup() throws IOException, InterruptedException {
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
                        session.logging.info(app + ": waiting for tomcat startup ... ");
                    }
                    Thread.sleep(100);
                }
            }
            for (int count = 1; !"STARTED".equals(state); count++) {
                if (count > 10 * 60 * 5) {
                    throw new IOException(app + ": tomcat startup timed out, state" + state);
                }
                if (count % 100 == 99) {
                    session.logging.info(app + ": waiting for tomcat startup ... " + state);
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
            session.logging.info("ignoring -tail option because container is not unique");
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

    public FileNode createContext(String app, FileNode war) throws IOException {
        FileNode result;

        result = directory.join("context").mkdirsOpt().join(app);
        result.deleteTreeOpt();
        result.mkdir();
        war.copyFile(result.join("app.war"));
        return result;
    }
}
