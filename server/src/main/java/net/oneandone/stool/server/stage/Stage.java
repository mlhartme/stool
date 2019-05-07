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
import net.oneandone.stool.server.ArgumentException;
import net.oneandone.stool.server.Server;
import net.oneandone.stool.server.configuration.Accessor;
import net.oneandone.stool.server.configuration.StageConfiguration;
import net.oneandone.stool.server.docker.BuildArgument;
import net.oneandone.stool.server.docker.BuildError;
import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.logging.AccessLogEntry;
import net.oneandone.stool.server.logging.LogReader;
import net.oneandone.stool.server.util.Field;
import net.oneandone.stool.server.util.Info;
import net.oneandone.stool.server.util.Ports;
import net.oneandone.stool.server.util.Property;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;

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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Represents the former backstage directory. From a Docker perspective, a stage roughly represents a Repository */
public class Stage {
    private static final DateTimeFormatter TAG_FORMAT = DateTimeFormatter.ofPattern("yyMMdd-HHmmss");

    private static final String IMAGE_PREFIX = "net.oneandone.stool-";
    private static final String CONTAINER_PREFIX = "net.oneandone.stool-container-";

    public static final String IMAGE_LABEL_PORT_DECLARED_PREFIX = IMAGE_PREFIX + "port.";
    public static final String IMAGE_LABEL_P12 = IMAGE_PREFIX + "certificate.p12";
    public static final String IMAGE_LABEL_MEMORY = IMAGE_PREFIX + "memory";
    public static final String IMAGE_LABEL_URL_CONTEXT = IMAGE_PREFIX + "url.server";
    public static final String IMAGE_LABEL_URL_SUFFIXES = IMAGE_PREFIX + "url.suffixes";
    public static final String IMAGE_LABEL_FAULT = IMAGE_PREFIX + "fault";
    public static final String IMAGE_LABEL_COMMENT = IMAGE_PREFIX + "comment";
    public static final String IMAGE_LABEL_ORIGIN = IMAGE_PREFIX + "origin";
    public static final String IMAGE_LABEL_CREATED_BY = IMAGE_PREFIX + "created-by";
    public static final String IMAGE_LABEL_CREATED_ON = IMAGE_PREFIX + "created-on";
    public static final String IMAGE_LABEL_ARG_PREFIX = IMAGE_PREFIX + "arg.";

    public static final String CONTAINER_LABEL_STAGE = CONTAINER_PREFIX + "rt.stage";
    public static final String CONTAINER_LABEL_IMAGE = CONTAINER_PREFIX + "rt.image";
    public static final String CONTAINER_LABEL_APP = CONTAINER_PREFIX + "rt.app";
    public static final String CONTAINER_LABEL_PORT_USED_PREFIX = CONTAINER_PREFIX + "rt.port.";


    //--

    public final Server server;
    private final String name;
    private final FileNode directory;
    public final StageConfiguration configuration;

    public Stage(Server server, FileNode directory, StageConfiguration configuration) {
        this.server = server;
        this.name = directory.getName();
        this.directory = directory;
        this.configuration = configuration;
    }

    public FileNode getDirectory() {
        return directory;
    }

    public String getName() {
        return name;
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
        for (Accessor type : server.accessors.values()) {
            result.add(new Property(type, configuration));
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
        // TODO: expensive - reads access logs multiple times

        List<Field> fields;

        fields = new ArrayList<>();
        fields.add(new Field("name") {
            @Override
            public Object get() {
                return name;
            }
        });
        fields.add(new Field("apps") {
            @Override
            public Object get() throws IOException {
                List<String> result;

                result = new ArrayList<>(images(server.dockerEngine()).keySet());
                Collections.sort(result);
                return result;
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
                return server.userManager.checkedByLogin(createdBy());
            }

        });
        fields.add(new Field("created-at") {
            @Override
            public Object get() throws IOException {
                // TODO: getCreated: https://unix.stackexchange.com/questions/7562/what-file-systems-on-linux-store-the-creation-time
                return oldest(accessLog(-1)).dateTime;
            }

        });
        fields.add(new Field("last-modified-by") {
            @Override
            public Object get() throws IOException {
                return server.userManager.checkedByLogin(youngest(accessLog(-1)).user);
            }
        });
        fields.add(new Field("last-modified-at") {
            @Override
            public Object get() throws IOException {
                return timespan(youngest(accessLog(-1)).dateTime);
            }
        });
        fields.add(new Field("urls") {
            @Override
            public Object get() throws IOException {
                return namedUrls(null);
            }
        });
        return fields;
    }

    public static String timespan(LocalDateTime ldt) {
        return timespan(ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
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
        configuration.save(server.gson, StageConfiguration.file(directory));
    }

    /** @return logins */
    public Set<String> notifyLogins() throws IOException {
        Set<String> done;
        String login;

        done = new HashSet<>();
        for (String user : configuration.notify) {
            switch (user) {
                case StageConfiguration.NOTIFY_LAST_MODIFIED_BY:
                    login = lastModifiedBy();
                    break;
                case StageConfiguration.NOTIFY_CREATED_BY:
                    login = createdBy();
                    break;
                default:
                    login = user;
            }
            done.add(login);
        }
        return done;
    }

    //-- docker

    public void wipeDocker(Engine engine) throws IOException {
        wipeContainer(engine);
        wipeImages(engine);
    }

    public void wipeImages(Engine engine) throws IOException {
        for (String tag : imageTags(engine)) {
            Server.LOGGER.debug("remove image: " + tag);
            engine.imageRemove(tag, false);
        }
    }

    private List<String> imageTags(Engine engine) throws IOException {
        Engine.ImageListInfo info;    /** @return list of tags belonging to this stage */

        List<String> result;

        result = new ArrayList<>();
        for (Map.Entry<String, Engine.ImageListInfo> entry : engine.imageList().entrySet()) {
            info = entry.getValue();
            for (String tag : info.tags) {
                if (tag.startsWith(server.configuration.registryNamespace + "/" + name + "/")) {
                    result.add(tag);
                }
            }
        }
        return result;
    }

    /** @return app mapped to sorted list */
    public Map<String, List<Image>> images(Engine engine) throws IOException {
        Map<String, List<Image>> result;
        Image image;
        List<Image> list;

        result = new HashMap<>();
        for (String tag : imageTags(engine)) {
            image = Image.load(engine, tag);
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
        int count;

        allImages = images(engine);
        for (String app : allImages.keySet()) {
            images = new ArrayList<>(allImages.get(app));
            count = images.size() - keep;
            while (count > 0 && !images.isEmpty()) {
                remove = images.remove(images.size() - 1).tag;
                if (engine.containerList(CONTAINER_LABEL_IMAGE, remove).isEmpty()) {
                    Server.LOGGER.debug("remove image: " + remove);
                    engine.imageRemove(remove, false);
                    count--;
                } else {
                    Server.LOGGER.debug("cannot remove image, because it's still in use: " + remove);
                }
            }
        }
    }

    public void wipeContainer(Engine engine) throws IOException {
        for (String tag : imageTags(engine)) {
            for (String container : engine.containerList(CONTAINER_LABEL_IMAGE, tag).keySet()) {
                Server.LOGGER.debug("remove container: " + container);
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

    public static class BuildResult {
        public final String output;
        public final String app;
        public final String tag;

        public BuildResult(String output, String app, String tag) {
            this.app = app;
            this.tag = tag;
            this.output = output;
        }
    }

    /** @param keep 0 to keep all */
    public BuildResult build(FileNode war, String comment, String origin,
                        String createdBy, String createdOn, boolean noCache, int keep,
                        Map<String, String> explicitArguments) throws Exception {
        Engine engine;
        String image;
        String app;
        String tag;
        FileNode context;
        Map<String, String> labels;
        Properties appProperties;
        FileNode template;
        Map<String, BuildArgument> defaults;
        Map<String, String> buildArgs;
        StringWriter output;
        String str;

        engine = this.server.dockerEngine();
        if (keep > 0) {
            wipeOldImages(engine,keep - 1);
        }
        appProperties = properties(war);
        template = template(appProperties, explicitArguments);
        app = app(appProperties, explicitArguments);
        tag = this.server.configuration.registryNamespace + "/" + name + "/" + app + ":" + TAG_FORMAT.format(LocalDateTime.now());
        defaults = BuildArgument.scan(template.join("Dockerfile"));
        buildArgs = buildArgs(defaults, appProperties, explicitArguments);
        context = dockerContext(app, war, template);
        labels = new HashMap<>();
        labels.put(IMAGE_LABEL_COMMENT, comment);
        labels.put(IMAGE_LABEL_ORIGIN, origin);
        labels.put(IMAGE_LABEL_CREATED_BY, createdBy);
        labels.put(IMAGE_LABEL_CREATED_ON, createdOn);
        for (Map.Entry<String, String> arg : buildArgs.entrySet()) {
            labels.put(IMAGE_LABEL_ARG_PREFIX + arg.getKey(), arg.getValue());
        }
        Server.LOGGER.debug("building image ... ");
        output = new StringWriter();
        try {
            image = engine.imageBuild(tag, buildArgs, labels, context, noCache, output);
        } catch (BuildError e) {
            Server.LOGGER.debug("image build output");
            Server.LOGGER.debug(e.output);
            throw e;
        } finally {
            output.close();
        }
        str = output.toString();
        Server.LOGGER.debug("successfully built image: " + image);
        Server.LOGGER.debug(str);
        return new BuildResult(str, app, tag);
    }

    /** @return apps actually started */
    public List<String> start(int http, int https, Map<String, String> environment, Map<String, Integer> selection) throws IOException {
        Engine engine;
        String container;
        Engine.Status status;
        Ports hostPorts;
        Map<FileNode, String> mounts;
        Map<String, String> labels;
        int unreserved;
        List<String> result;

        engine = server.dockerEngine();
        unreserved = server.memUnreserved();
        result = new ArrayList<>();
        for (Image image : resolve(engine, selection)) {
            if (image.memory > unreserved) {
                throw new ArgumentException("Cannot reserve memory for app " + image.app + " :\n"
                        + "  unreserved: " + unreserved + "\n"
                        + "  requested: " + image.memory + "\n"
                        + "Consider stopping stages.");
            }
            for (String old : engine.containerList(Stage.CONTAINER_LABEL_IMAGE, image.tag).keySet()) {
                engine.containerRemove(old);
            }
            Server.LOGGER.debug("environment: " + environment);
            Server.LOGGER.info(image.app + ": starting container ... ");
            mounts = bindMounts(image);
            for (Map.Entry<FileNode, String> mount : mounts.entrySet()) {
                Server.LOGGER.debug("  " + mount.getKey().getAbsolute() + "\t -> " + mount.getValue());
            }
            hostPorts = server.pool().allocate(this, image.app, http, https);
            labels = hostPorts.toUsedLabels();
            labels.put(CONTAINER_LABEL_APP, image.app);
            labels.put(CONTAINER_LABEL_IMAGE, image.tag);
            labels.put(CONTAINER_LABEL_STAGE, name);
            container = engine.containerCreate(image.tag,  getName() + "." + server.configuration.dockerHost,
                    OS.CURRENT == OS.MAC /* TODO: why */, 1024L * 1024 * image.memory, null, null,
                    labels, environment, mounts, image.ports.map(hostPorts));
            Server.LOGGER.debug("created container " + container);
            engine.containerStart(container);
            status = engine.containerStatus(container);
            if (status != Engine.Status.RUNNING) {
                throw new IOException("unexpected status: " + status);
            }
            result.add(image.app);
        }
        return result;
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
            throw new ArgumentException("no apps to start - did you build the stage?");
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
            if (!running.contains(app)) {
                idx = entry.getValue();
                list = allImages.get(app);
                if (list == null) {
                    throw new ArgumentException("app not found: " + app);
                }
                if (idx < 0 || idx >= list.size()) {
                    throw new ArgumentException(app + ": app index not found: " + idx);
                }
                result.add(list.get(idx));
            }
        }
        return result;
    }

    /** @return list of applications actually stopped */
    public List<String> stop(List<String> apps) throws IOException {
        Map<String, Current> currentMap;
        Engine engine;
        Map<String, String> containers;
        List<String> unknown;

        unknown = new ArrayList<>(apps);
        unknown.removeAll(images(server.dockerEngine()).keySet());
        if (!unknown.isEmpty()) {
            throw new ArgumentException("unknown app(s): " + unknown);
        }
        currentMap = currentMap();
        containers = new LinkedHashMap<>();
        for (Map.Entry<String, Current> current : currentMap.entrySet()) {
            if (apps.isEmpty() || apps.contains(current.getKey())) {
                containers.put(current.getKey(), current.getValue().container);
            }
        }
        for (Map.Entry<String, String> entry : containers.entrySet()) {
            Server.LOGGER.info(entry.getKey() + ": stopping container ...");
            engine = server.dockerEngine();
            engine.containerStop(entry.getValue(), 300);
        }
        return new ArrayList<>(containers.keySet());
    }

    private Map<FileNode, String> bindMounts(Image image) throws IOException {
        FileNode hostLogRoot;
        Map<FileNode, String> result;
        List<String> missing;
        FileNode innerFile;
        FileNode outerFile;

        hostLogRoot = server.configuration.serverHome.join("stages", getName(), "logs", image.app);
        directory.join("logs", image.app).mkdirsOpt();
        result = new HashMap<>();
        result.put(hostLogRoot, "/var/log/stool");
        if (image.ports.https != -1) {
            if (image.p12 != null) {
                result.put(server.certificate(server.configuration.vhosts ? image.app + "." + getName() + "." + server.configuration.dockerHost
                        : server.configuration.dockerHost), image.p12);
            }
        }
        missing = new ArrayList<>();
        for (String project : image.faultProjects) { // TODO: authorization
            innerFile = server.world.file("/etc/fault/workspace").join(project);
            outerFile = server.world.file(server.configuration.secrets).join(project);
            if (innerFile.isDirectory()) {
                result.put(outerFile, "/root/.fault/" + project);
            } else {
                missing.add(outerFile.getAbsolute());
            }
        }
        if (!missing.isEmpty()) {
            throw new ArgumentException("missing secret directories: " + missing);
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
        Node<?> node;
        Properties all;
        Properties result;
        String prefix;

        prefix = server.configuration.appPropertiesPrefix;
        node = war.openZip().join(server.configuration.appPropertiesFile);
        result = new Properties();
        if (node.exists()) {
            all = node.readProperties();
            for (String name : all.stringPropertyNames()) {
                if (name.startsWith(prefix)) {
                    result.setProperty(name.substring(prefix.length()), all.getProperty(name));
                }
            }
        }
        return result;
    }

    private FileNode template(Properties appProperies, Map<String, String> explicit) throws IOException {
        return server.templates().join(eat(appProperies, explicit,"_template", "war")).checkDirectory();
    }

    private String app(Properties appProperties, Map<String, String> explitit) {
        return eat(appProperties, explitit,"_app", "app");
    }

    private String eat(Properties appProperties, Map<String, String> explicit, String key, String dflt) {
        Object appValue;
        String explicitValue;

        explicitValue = explicit.remove(key);
        appValue = appProperties.remove(key);
        if (explicitValue != null) {
            return explicitValue;
        }
        return appValue == null ? dflt : appValue.toString();
    }

    private Map<String, String> buildArgs(Map<String, BuildArgument> defaults, Properties appProperties, Map<String, String> explicit) {
        Map<String, String> result;
        String name;

        result = new HashMap<>();
        for (BuildArgument arg : defaults.values()) {
            result.put(arg.name, arg.dflt);
        }
        for (Map.Entry<Object, Object> entry : appProperties.entrySet()) {
            name = entry.getKey().toString();
            if (!result.containsKey(name)) {
                throw new ArgumentException("unknown build argument in stool.properties: " + name);
            }
            result.put(name, entry.getValue().toString());
        }
        for (Map.Entry<String, String> entry : explicit.entrySet()) {
            name = entry.getKey();
            if (!result.containsKey(name)) {
                throw new ArgumentException("unknown explicit build argument: " + name);
            }
            result.put(name, entry.getValue());
        }
        return result;
    }

    //--

    /** @return login name */
    public String createdBy() throws IOException {
        return oldest(accessLog(-1)).user;
    }

    //--

    /** maps app to its ports; empty map if not ports allocated yet */
    public Map<String, Ports> loadPorts() throws IOException {
        return server.pool().stage(name);
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
        engine = server.dockerEngine();
        images = new HashMap<>();
        for (Engine.ContainerListInfo info : engine.containerList(Stage.CONTAINER_LABEL_IMAGE).values()) {
            if (name.equals(info.labels.get(Stage.CONTAINER_LABEL_STAGE))) {
                images.put(info.labels.get(Stage.CONTAINER_LABEL_APP), Image.load(engine, info.labels.get(CONTAINER_LABEL_IMAGE)));
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

        hostname = server.configuration.dockerHost;
        if (server.configuration.vhosts) {
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

    public void remove() throws IOException {
        wipeDocker(server.dockerEngine());
        getDirectory().deleteTree();
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

    public List<String> dockerRunningContainerList() throws IOException {
        Engine engine;

        engine = server.dockerEngine();
        return new ArrayList<>(engine.containerListRunning(CONTAINER_LABEL_STAGE, name).keySet());
    }

    public Map<String, Current> currentMap() throws IOException {
        Engine engine;
        List<String> containerList;
        JsonObject json;
        Map<String, Current> result;
        Image image;

        engine = server.dockerEngine();
        result = new HashMap<>();
        containerList = dockerRunningContainerList();
        for (String container : containerList) {
            json = engine.containerInspect(container, false);
            image = Image.load(engine, Server.containerImageTag(json));
            result.put(image.app, new Current(image, container));
        }
        return result;
    }

    public int contentHash() throws IOException {
        return ("StageInfo{"
                + "name='" + name + '\''
                + ", comment='" + configuration.comment + '\''
                // TODO: current image, container?
                + ", urls=" + urlMap(null)
                + ", running=" + dockerRunningContainerList()
                + '}').hashCode();
    }

    public String lastModifiedBy() throws IOException {
        return youngest(accessLog(-1)).user;
    }

    /** @return last entry first */
    public List<AccessLogEntry> accessLog(int max) throws IOException {
        AccessLogEntry entry;
        List<AccessLogEntry> entries;
        LogReader<AccessLogEntry> reader;
        String stage;
        String previousInvocation;

        entries = new ArrayList<>();
        reader = server.accessLogReader();
        stage = getName();
        while (true) {
            entry = reader.prev();
            if (entry == null) {
                break;
            }
            if (stage.equals(entry.stageName)) {
                previousInvocation = entries.isEmpty() ? "" : entries.get(entries.size() - 1).clientInvocation;
                if (!entry.clientInvocation.equals(previousInvocation)) {
                    entries.add(entry);
                }
                if (entries.size() == max) {
                    break;
                }
            }
        }
        return entries;
    }

    private static AccessLogEntry youngest(List<AccessLogEntry> accessLog) {
        return accessLog.get(0);
    }
    private static AccessLogEntry oldest(List<AccessLogEntry> accessLog) {
        return accessLog.get(accessLog.size() - 1);
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

    public void awaitStartup() throws IOException {
        String app;
        Ports ports;
        String state;

        Server.LOGGER.info("await startup ...");
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
                        Server.LOGGER.info(app + ": waiting for tomcat startup ... ");
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        // fall-through
                    }
                }
            }
            for (int count = 1; !"STARTED".equals(state); count++) {
                if (count > 10 * 60 * 5) {
                    throw new IOException(app + ": tomcat startup timed out, state" + state);
                }
                if (count % 100 == 99) {
                    Server.LOGGER.info(app + ": waiting for tomcat startup ... " + state);
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    // fall-through
                }
                state = jmxEngineState(ports);
            }
        }
    }

    private JMXConnector jmxConnection(Ports ports) throws IOException {
        JMXServiceURL url;

        // see https://docs.oracle.com/javase/tutorial/jmx/remote/custom.html
        try {
            url = new JMXServiceURL("service:jmx:jmxmp://" + server.configuration.dockerHost + ":" + ports.jmxmp);
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

        containers = dockerRunningContainerList();
        if (containers.size() != 1) {
            Server.LOGGER.info("ignoring -tail option because container is not unique");
        } else {
            engine = server.dockerEngine();
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

        result = directory.join("server").mkdirsOpt().join(app);
        result.deleteTreeOpt();
        result.mkdir();
        war.copyFile(result.join("app.war"));
        return result;
    }
}
