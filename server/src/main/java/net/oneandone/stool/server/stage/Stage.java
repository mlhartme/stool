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
import net.oneandone.stool.server.docker.ContainerInfo;
import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.docker.ImageInfo;
import net.oneandone.stool.server.logging.AccessLogEntry;
import net.oneandone.stool.server.util.AppInfo;
import net.oneandone.stool.server.util.Context;
import net.oneandone.stool.server.util.Field;
import net.oneandone.stool.server.util.Info;
import net.oneandone.stool.server.util.Pool;
import net.oneandone.stool.server.util.Ports;
import net.oneandone.stool.server.util.Property;
import net.oneandone.sushi.fs.GetLastModifiedException;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
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
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * A collection of apps, each of them with images. From a Docker perspective, a stage roughly represents a Repository.
 * A short-lived object, created for one request, discarded afterwards - caches results for performance.
 */
public class Stage {
    private static final String IMAGE_PREFIX = "net.oneandone.stool-";
    private static final String CONTAINER_PREFIX = "net.oneandone.stool-container-";

    public static final String IMAGE_LABEL_PORT_DECLARED_PREFIX = IMAGE_PREFIX + "port.";
    public static final String IMAGE_LABEL_P12 = IMAGE_PREFIX + "certificate.p12";
    public static final String IMAGE_LABEL_DISK = IMAGE_PREFIX + "disk";
    public static final String IMAGE_LABEL_MEMORY = IMAGE_PREFIX + "memory";
    public static final String IMAGE_LABEL_URL_CONTEXT = IMAGE_PREFIX + "url.context";
    public static final String IMAGE_LABEL_URL_SUFFIXES = IMAGE_PREFIX + "url.suffixes";
    public static final String IMAGE_LABEL_FAULT = IMAGE_PREFIX + "fault";
    public static final String IMAGE_LABEL_COMMENT = IMAGE_PREFIX + "comment";
    public static final String IMAGE_LABEL_ORIGIN_SCM = IMAGE_PREFIX + "origin-scm";
    public static final String IMAGE_LABEL_ORIGIN_USER = IMAGE_PREFIX + "origin-user";
    public static final String IMAGE_LABEL_CREATED_BY = IMAGE_PREFIX + "created-by";
    public static final String IMAGE_LABEL_ARG_PREFIX = IMAGE_PREFIX + "arg.";

    public static final String CONTAINER_LABEL_STAGE = CONTAINER_PREFIX + "stage";
    public static final String CONTAINER_LABEL_IMAGE = CONTAINER_PREFIX + "image";
    public static final String CONTAINER_LABEL_APP = CONTAINER_PREFIX + "app";
    public static final String CONTAINER_LABEL_ENV_PREFIX = CONTAINER_PREFIX  + "env.";
    public static final String CONTAINER_LABEL_PORT_USED_PREFIX = CONTAINER_PREFIX + "port.";


    //--

    public final Server server;
    private final String name;

    /** CAUTION: not thread safe */
    private final FileNode directory;

    public final StageConfiguration configuration;

    public Stage(Server server, FileNode directory, StageConfiguration configuration) {
        this.server = server;
        this.name = directory.getName();
        this.directory = directory;
        this.configuration = configuration;
    }

    public String getName() {
        return name;
    }

    public FileNode getDirectory() {
        return directory;
    }

    /** for application logs */
    public FileNode logs() {
        return directory.join("logs");
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

    public Property propertyOpt(String property) {
        for (Property candidate : properties()) {
            if (property.equals(candidate.name())) {
                return candidate;
            }
        }
        return null;
    }

    public List<Field> fields() {
        List<Field> fields;

        fields = new ArrayList<>();
        fields.add(new Field("name") {
            @Override
            public Object get(Context context) {
                return name;
            }
        });
        fields.add(new Field("apps") {
            @Override
            public Object get(Context context) throws IOException {
                List<String> result;

                result = new ArrayList<>(context.images(Stage.this).keySet());
                Collections.sort(result);
                return result;
            }
        });
        fields.add(new Field("running") {
            @Override
            public Object get(Context context) throws IOException {
                Map<String, Current> map;
                List<String> result;

                map = context.currentMap(Stage.this);
                result = new ArrayList<>();
                for (Map.Entry<String, Current> entry : map.entrySet()) {
                    result.add(entry.getKey() + ":" + entry.getValue().image.tag);
                }
                Collections.sort(result);
                return result;
            }
        });
        fields.add(new Field("created-by") {
            @Override
            public Object get(Context context) throws IOException {
                String login;

                login = createdBy();
                return login == null ? null : server.userManager.checkedByLogin(login);
            }

        });
        fields.add(new Field("created-at") {
            @Override
            public Object get(Context context) throws IOException {
                // I can't ask the filesystem, see
                // https://unix.stackexchange.com/questions/7562/what-file-systems-on-linux-store-the-creation-time
                AccessLogEntry entry;

                entry = oldest(accessLogModifiedOnly());
                return entry == null ? null : entry.dateTime;
            }

        });
        fields.add(new Field("last-modified-by") {
            @Override
            public Object get(Context context) throws IOException {
                AccessLogEntry entry;

                entry = youngest(accessLogModifiedOnly());
                return entry == null ? null : server.userManager.checkedByLogin(entry.user);
            }
        });
        fields.add(new Field("last-modified-at") {
            @Override
            public Object get(Context context) throws IOException {
                AccessLogEntry entry;

                entry = youngest(accessLogModifiedOnly());
                return entry == null ? null : timespan(entry.dateTime);
            }
        });
        fields.add(new Field("urls") {
            @Override
            public Object get(Context context) throws IOException {
                return urlMap(context.engine, server.pool, null);
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
            if (login == null) {
                done.add(login);
            }
        }
        return done;
    }

    //-- docker

    public void wipeDocker(Engine engine) throws IOException {
        wipeContainer(engine);
        wipeImages(engine);
    }

    public void wipeImages(Engine engine) throws IOException {
        for (String repositoryTag : imageTags(engine)) {
            Server.LOGGER.debug("remove image: " + repositoryTag);
            engine.imageRemove(repositoryTag, false);
        }
    }

    /** @return list of tags belonging to this stage */
    private List<String> imageTags(Engine engine) throws IOException {
        ImageInfo info;
        List<String> result;

        result = new ArrayList<>();
        for (Map.Entry<String, ImageInfo> entry : engine.imageList().entrySet()) {
            info = entry.getValue();
            for (String repositoryTag : info.repositoryTags) {
                if (repositoryTag.startsWith(server.configuration.registryNamespace + "/" + name + "/")) {
                    result.add(repositoryTag);
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
        for (String repositoryTag : imageTags(engine)) {
            image = Image.load(engine, repositoryTag);
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

    /** next version */
    public int wipeOldImages(Engine engine, String app, int keep) throws IOException {
        List<Image> images;
        String remove;
        int count;
        int result;

        images = images(engine).get(app);
        if (images == null) {
            return 1;
        }
        result = Image.nextTag(images);
        count = images.size() - keep;
        while (count > 0 && !images.isEmpty()) {
            remove = images.remove(0).repositoryTag;
            if (engine.containerList(CONTAINER_LABEL_IMAGE, remove).isEmpty()) {
                Server.LOGGER.debug("remove image: " + remove);
                engine.imageRemove(remove, false);
                count--;
            } else {
                Server.LOGGER.debug("cannot remove image, because it's still in use: " + remove);
            }
        }
        return result;
    }

    public void wipeContainer(Engine engine) throws IOException {
        for (String repositoryTag : imageTags(engine)) {
            for (String container : engine.containerList(CONTAINER_LABEL_IMAGE, repositoryTag).keySet()) {
                Server.LOGGER.debug("remove container: " + container);
                engine.containerRemove(container);
            }
        }
    }

    public void checkExpired() {
        if (configuration.expire.isExpired()) {
            throw new ArgumentException("Stage expired " + configuration.expire + ". To start it, you have to adjust the 'expire' date.");
        }
    }

    // --storage-opt size=42m could limit disk space, but it's only available for certain storage drivers (with certain mount options) ...
    public void checkDiskQuota(Engine engine) throws IOException {
        int used;
        int quota;
        Map<String, Current> map;
        ContainerInfo info;

        map = currentMap(engine);
        for (Current current : map.values()) {
            info = current.container;
            if (info != null) {
                used = AppInfo.sizeRw(engine, info);
                quota = current.image.disk;
                if (used > quota) {
                    throw new ArgumentException("Stage disk quota exceeded. Used: " + used + " mb  >  quota: " + quota + " mb.\n");
                }
            }
        }
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

    /**
     * @param keep 0 to keep all  */
    public BuildResult buildandEatWar(Engine engine, FileNode war, String comment, String originScm,
                                      String originUser, String createdBy, boolean noCache, int keep,
                                      Map<String, String> explicitArguments) throws Exception {
        int tag;
        String image;
        String app;
        String repositoryTag;
        FileNode context;
        Map<String, String> labels;
        Properties appProperties;
        FileNode template;
        Map<String, BuildArgument> defaults;
        Map<String, String> buildArgs;
        StringWriter output;
        String str;

        appProperties = properties(war);
        template = template(appProperties, explicitArguments);
        app = app(appProperties, explicitArguments);
        tag = wipeOldImages(engine, app, keep - 1);
        context = createContextEatWar(app, war);  // this is where concurrent builds are blocked
        try {
            repositoryTag = this.server.configuration.registryNamespace + "/" + name + "/" + app + ":" + tag;
            defaults = BuildArgument.scan(template.join("Dockerfile"));
            buildArgs = buildArgs(defaults, appProperties, explicitArguments);
            populateContext(context, template);
            labels = new HashMap<>();
            labels.put(IMAGE_LABEL_COMMENT, comment);
            labels.put(IMAGE_LABEL_ORIGIN_SCM, originScm);
            labels.put(IMAGE_LABEL_ORIGIN_USER, originUser);
            labels.put(IMAGE_LABEL_CREATED_BY, createdBy);
            for (Map.Entry<String, String> arg : buildArgs.entrySet()) {
                labels.put(IMAGE_LABEL_ARG_PREFIX + arg.getKey(), arg.getValue());
            }
            Server.LOGGER.debug("building context " + context.getAbsolute());
            output = new StringWriter();
            try {
                image = engine.imageBuild(repositoryTag, buildArgs, labels, context, noCache, output);
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
        } finally {
            cleanupContext(app, Integer.toString(tag), keep);
        }
        return new BuildResult(str, app, Integer.toString(tag));
    }

    /** @return images actually started */
    public List<String> start(Engine engine, Pool pool, int http, int https, Map<String, String> clientEnvironment, Map<String, String> selection) throws IOException {
        String container;
        Engine.Status status;
        Ports hostPorts;
        Map<String, String> environment;
        Map<FileNode, String> mounts;
        Map<String, String> labels;
        List<String> result;
        int memoryQuota;
        int memoryReserved;

        server.sshDirectory.update(); // ports may change - make sure to wipe outdated keys
        memoryReserved = server.memoryReservedContainers(engine);
        result = new ArrayList<>();
        memoryQuota = server.configuration.memoryQuota;
        for (Image image : resolve(engine, selection)) {
            if (memoryQuota != 0 && memoryReserved + image.memory > memoryQuota) {
                throw new ArgumentException("Cannot reserve memory for app " + image.app + " :\n"
                        + "  unreserved: " + (memoryQuota - memoryReserved) + "\n"
                        + "  requested: " + image.memory + "\n"
                        + "Consider stopping stages.");
            }
            memoryReserved += image.memory;
            for (ContainerInfo info : engine.containerList(CONTAINER_LABEL_STAGE, name).values()) {
                if (info.labels.get(CONTAINER_LABEL_APP).equals(image.app)) {
                    Server.LOGGER.debug("wipe old image: " + info.id);
                    engine.containerRemove(info.id);
                }
            }
            environment = new HashMap<>(server.configuration.environment);
            environment.putAll(configuration.environment);
            environment.putAll(clientEnvironment);
            Server.LOGGER.debug("environment: " + environment);
            Server.LOGGER.info(image.app + ": starting container ... ");
            mounts = bindMounts(image);
            for (Map.Entry<FileNode, String> mount : mounts.entrySet()) {
                Server.LOGGER.debug("  " + mount.getKey().getAbsolute() + "\t -> " + mount.getValue());
            }
            hostPorts = pool.allocate(this, image.app, http, https);
            labels = hostPorts.toUsedLabels();
            labels.put(CONTAINER_LABEL_APP, image.app);
            labels.put(CONTAINER_LABEL_IMAGE, image.repositoryTag);
            labels.put(CONTAINER_LABEL_STAGE, name);
            for (Map.Entry<String, String> entry : environment.entrySet()) {
                labels.put(CONTAINER_LABEL_ENV_PREFIX + entry.getKey(), entry.getValue());
            }
            container = engine.containerCreate(toName(image.repositoryTag), image.repositoryTag,
                    md5(getName()) + "." + server.configuration.dockerHost, server.networkMode,
                    false, 1024L * 1024 * image.memory, null, null,
                    labels, environment, mounts, image.ports.map(hostPorts, server.localhostIp));
            Server.LOGGER.debug("created container " + container);
            engine.containerStart(container);
            status = engine.containerStatus(container);
            if (status != Engine.Status.RUNNING) {
                throw new IOException("unexpected status: " + status);
            }
            result.add(image.app + ":" + image.tag);
        }
        return result;
    }

    private static String md5(String str) {
        MessageDigest md;
        byte[] bytes;
        String result;

        try {
            md = MessageDigest.getInstance("MD5");
            bytes = md.digest(str.getBytes("UTF-8")); //converting byte array to Hexadecimal
            result = Strings.toHex(bytes);
            if (result.length() != 32) {
                throw new IllegalStateException(str + " " + result);
            }
            return result;
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {
            throw new IllegalStateException();
        }
    }

    private static String toName(String str) {
        StringBuilder result;
        char c;

        result = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            c = str.charAt(i);
            switch (c) {
                case '-':
                case '_':
                    result.append(c);
                    break;
                case '.':
                case ':':
                case '/':
                    result.append('_');
                    break;
                default:
                    if ((c >= 'a' && c <='z') || (c >= 'A' && c <='Z') || (c >= '0' && c <='9')) {
                        result.append(c);
                    } else {
                        result.append(Integer.toString(c));
                    }
            }
        }
        return result.toString();
    }

    private List<Image> resolve(Engine engine, Map<String, String> selectionOrig) throws IOException {
        Map<String, String> selection;
        Map<String, List<Image>> allImages;
        Collection<String> running;
        String app;
        String tag;
        List<Image> list;
        List<Image> result;
        Image image;

        allImages = images(engine);
        if (allImages.isEmpty()) {
            throw new ArgumentException("no apps to start - did you build the stage?");
        }
        running = currentMap(engine).keySet();
        if (selectionOrig.isEmpty()) {
            selection = new HashMap<>();
            for (String a : allImages.keySet()) {
                selection.put(a, "");
            }
        } else {
            selection = selectionOrig;
        }
        result = new ArrayList<>();
        for (Map.Entry<String, String> entry : selection.entrySet()) {
            app = entry.getKey();
            if (!running.contains(app)) {
                tag = entry.getValue();
                list = allImages.get(app);
                if (list == null || list.isEmpty()) {
                    throw new ArgumentException("app not found: " + app);
                }
                image = lookup(list, tag);
                if (image == null) {
                    throw new ArgumentException("image not found: " + app + ":" + tag);
                }
                result.add(image);
            }
        }
        return result;
    }

    private static Image lookup(List<Image> images, String tag) {
        if (tag.isEmpty()) {
            return images.get(images.size() - 1);
        }
        for (Image image : images) {
            if (image.tag.equals(tag)) {
                return image;
            }
        }
        return null;
    }

    /** @return list of applications actually stopped */
    public List<String> stop(Engine engine, List<String> apps) throws IOException {
        Map<String, Current> currentMap;
        Map<String, String> containers; // maps app:tag to containerId
        List<String> unknown;

        server.sshDirectory.update(); // ports may change - make sure to wipe outdated keys
        unknown = new ArrayList<>(apps);
        unknown.removeAll(images(engine).keySet());
        if (!unknown.isEmpty()) {
            throw new ArgumentException("unknown app(s): " + unknown);
        }
        currentMap = currentMap(engine);
        containers = new LinkedHashMap<>();
        for (Map.Entry<String, Current> current : currentMap.entrySet()) {
            if (apps.isEmpty() || apps.contains(current.getKey())) {
                containers.put(current.getKey() + ":" + current.getValue().image.tag, current.getValue().container.id);
            }
        }
        for (Map.Entry<String, String> entry : containers.entrySet()) {
            Server.LOGGER.info(entry.getKey() + ": stopping container ...");
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

        hostLogRoot = server.serverHome.join("stages", getName(), "logs", image.app);
        logs().join(image.app).mkdirsOpt();
        result = new HashMap<>();
        result.put(hostLogRoot, "/var/log/stool");
        if (image.ports.https != -1) {
            if (image.p12 != null) {
                result.put(server.certificate(server.configuration.vhosts ? image.app + "." + getName() + "." + server.configuration.dockerHost
                        : server.configuration.dockerHost), image.p12);
            }
        }
        missing = new ArrayList<>();
        if (server.configuration.auth()) {
            server.checkFaultPermissions(image.createdBy, image.faultProjects);
        }
        for (String project : image.faultProjects) {
            innerFile = directory.getWorld().file("/etc/fault/workspace").join(project);
            outerFile = server.secrets.join(project);
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

    private void populateContext(FileNode context, FileNode src) throws IOException {
        FileNode destparent;
        FileNode destfile;

        for (FileNode srcfile : src.find("**/*")) {
            if (srcfile.isDirectory()) {
                continue;
            }
            destfile = context.join(srcfile.getRelative(src));
            destparent = destfile.getParent();
            destparent.mkdirsOpt();
            srcfile.copy(destfile);
        }
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
            for (String property : all.stringPropertyNames()) {
                if (property.startsWith(prefix)) {
                    result.setProperty(property.substring(prefix.length()), all.getProperty(property));
                }
            }
        }
        return result;
    }

    private FileNode template(Properties appProperies, Map<String, String> explicit) throws IOException {
        return server.templates().join(eat(appProperies, explicit, "_template", "war")).checkDirectory();
    }

    private String app(Properties appProperties, Map<String, String> explitit) {
        return eat(appProperties, explitit, "_app", "app");
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
        String property;

        result = new HashMap<>();
        for (BuildArgument arg : defaults.values()) {
            result.put(arg.name, arg.dflt);
        }
        for (Map.Entry<Object, Object> entry : appProperties.entrySet()) {
            property = entry.getKey().toString();
            if (!result.containsKey(property)) {
                throw new ArgumentException("unknown build argument in stool.properties: " + property + "\n" + available(defaults.values()));
            }
            result.put(property, entry.getValue().toString());
        }
        for (Map.Entry<String, String> entry : explicit.entrySet()) {
            property = entry.getKey();
            if (!result.containsKey(property)) {
                throw new ArgumentException("unknown explicit build argument: " + property + "\n" + available(defaults.values()));
            }
            result.put(property, entry.getValue());
        }
        return result;
    }

    private static String available(Collection<BuildArgument> args) {
        StringBuilder result;

        result = new StringBuilder();
        result.append("(available build arguments:");
        for (BuildArgument arg : args) {
            result.append(' ');
            result.append(arg.name);
        }
        result.append(")\n");
        return result.toString();
    }

    //--

    /** @return login name or null if unkonwn */
    public String createdBy() throws IOException {
        AccessLogEntry entry;

        entry = oldest(accessLogModifiedOnly());
        return entry == null ? null : entry.user;
    }

    //--

    public Map<String, String> urlMap(Engine engine, Pool pool, String oneApp) throws IOException {
        return urlMap(engine, pool, allContainerMap(engine).values(), oneApp);
    }

    /**
     * @param oneApp null for all apps
     * @return empty map if no ports are allocated
     */
    public Map<String, String> urlMap(Engine engine, Pool pool, Collection<ContainerInfo> allContainerList, String oneApp) throws IOException {
        Map<String, String> result;
        String app;
        Map<String, Image> images;

        result = new LinkedHashMap<>();
        images = new HashMap<>();
        for (ContainerInfo info : allContainerList) {
            if (name.equals(info.labels.get(Stage.CONTAINER_LABEL_STAGE))) {
                images.put(info.labels.get(Stage.CONTAINER_LABEL_APP), Image.load(engine, info.labels.get(CONTAINER_LABEL_IMAGE)));
            }
        }
        for (Map.Entry<String, Ports> entry : pool.stage(name).entrySet()) {
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
            addNamed(app, url(image, "http", ports.http), dest);
        }
        if (ports.https != -1) {
            addNamed(app + " SSL", url(image, "https", ports.https), dest);
        }
    }

    private void addNamed(String key, List<String> urls, Map<String, String> dest) {
        int count;

        if (urls.size() == 1) {
            dest.put(key, urls.get(0));
        } else {
            count = 1;
            for (String url : urls) {
                dest.put(key + "_" + count, url);
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

    /** @return empty list if no ports are allocated */
    public List<String> namedUrls(Engine engine, Pool pool, String oneApp) throws IOException {
        List<String> result;

        result = new ArrayList<>();
        for (Map.Entry<String, String> entry : urlMap(engine, pool, oneApp).entrySet()) {
            result.add(entry.getKey() + " " + entry.getValue());
        }
        return result;
    }

    public void remove(Engine engine) throws IOException {
        wipeDocker(engine);
        server.pool.remove(name);
        getDirectory().deleteTree();
    }

    //--

    public static class Current {
        public final Image image;
        public final ContainerInfo container;

        public Current(Image image, ContainerInfo container) {
            this.image = image;
            this.container = container;
        }
    }

    // not just this stage
    public static Map<String, ContainerInfo> allContainerMap(Engine engine) throws IOException {
        return engine.containerList(Stage.CONTAINER_LABEL_IMAGE);
    }

    public Map<String, ContainerInfo> runningContainerMap(Map<String, ContainerInfo> allContainerMap) {
        Map<String, ContainerInfo> result;
        ContainerInfo info;

        result = new HashMap<>();
        for (Map.Entry<String, ContainerInfo> entry : allContainerMap.entrySet()) {
            info = entry.getValue();
            if (info.state == Engine.Status.RUNNING && name.equals(info.labels.get(CONTAINER_LABEL_STAGE))) {
                result.put(entry.getKey(), info);
            }
        }
        return result;
    }

    public Map<String, ContainerInfo> runningContainerMap(Engine engine) throws IOException {
        return engine.containerListRunning(CONTAINER_LABEL_STAGE, name);
    }

    /** only for running apps, does not include stopped apps */
    public Map<String, Current> currentMap(Engine engine) throws IOException {
        return currentMap(engine, runningContainerMap(engine).values());
    }

    public Map<String, Current> currentMap(Engine engine, Collection<ContainerInfo> runningContainerList) throws IOException {
        Map<String, Current> result;
        Image image;

        result = new HashMap<>();
        for (ContainerInfo info : runningContainerList) {
            image = Image.load(engine, info.labels.get(CONTAINER_LABEL_IMAGE));
            result.put(image.app, new Current(image, info));
        }
        return result;
    }

    /** @return null if unknown */
    public String lastModifiedBy() throws IOException {
        AccessLogEntry entry;

        entry = youngest(accessLogModifiedOnly());
        return entry == null ? null : entry.user;
    }


    private List<AccessLogEntry> cachedAccessLogModifiedOnly = null;

    /** @return last entry first; list may be empty because old log files are removed. */
    public List<AccessLogEntry> accessLogModifiedOnly() throws IOException {
        if (cachedAccessLogModifiedOnly == null) {
            cachedAccessLogModifiedOnly = server.accessLog(getName(), -1, true);
        }
        return cachedAccessLogModifiedOnly;
    }

    /** @return last entry first; list may be empty because old log files are removed. */
    public List<AccessLogEntry> accessLogAll(int max) throws IOException {
        return server.accessLog(getName(), max, false);
    }

    /* @return null if unknown (e.g. because log file was wiped) */
    private static AccessLogEntry youngest(List<AccessLogEntry> accessLog) {
        return accessLog.isEmpty() ? null : accessLog.get(0);
    }


    /* @return null if unkown (e.g. because log file was wiped) */
    private static AccessLogEntry oldest(List<AccessLogEntry> accessLog) {
        return accessLog.isEmpty() ? null : accessLog.get(accessLog.size() - 1);
    }

    //--

    public void awaitStartup(Engine engine) throws IOException {
        Map<String, JMXServiceURL> jmxMap;
        String app;
        JMXServiceURL url;
        String state;

        jmxMap = jmxMap(engine);
        Server.LOGGER.info("await startup ... ");
        for (Map.Entry<String, JMXServiceURL> entry : jmxMap.entrySet()) {
            app = entry.getKey();
            url = entry.getValue();
            for (int count = 0; true; count++) {
                try {
                    state = jmxEngineState(url);
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
                state = jmxEngineState(url);
            }
        }
    }

    public Map<String, JMXServiceURL> jmxMap(Engine engine) throws IOException {
        Map<String, JMXServiceURL> result;
        JsonObject inspected;
        JsonObject networks;
        JsonObject network;
        String app;
        String ip;
        String jmx;
        Collection<ContainerInfo> containerList;

        containerList = runningContainerMap(engine).values();
        result = new HashMap<>();
        for (ContainerInfo info : containerList) {
            inspected = engine.containerInspect(info.id, false);
            networks = inspected.get("NetworkSettings").getAsJsonObject().get("Networks").getAsJsonObject();
            if (networks.size() != 1) {
                throw new IOException("unexpected Networks: " + networks);
            }
            network = networks.entrySet().iterator().next().getValue().getAsJsonObject();
            ip = network.get("IPAddress").getAsString();
            jmx = info.labels.get(IMAGE_LABEL_PORT_DECLARED_PREFIX + Ports.Port.JMXMP.toString().toLowerCase());
            app = info.labels.get(CONTAINER_LABEL_APP);
            // see https://docs.oracle.com/javase/tutorial/jmx/remote/custom.html
            try {
                result.put(app, new JMXServiceURL("service:jmx:jmxmp://" + ip + ":" + jmx));
            } catch (MalformedURLException e) {
                throw new IllegalStateException(e);
            }

        }
        return result;
    }


    private String jmxEngineState(JMXServiceURL url) throws IOException {
        ObjectName object;

        try {
            object = new ObjectName("Catalina:type=Engine");
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException(e);
        }
        try (JMXConnector connection = JMXConnectorFactory.connect(url, null)) {
            return (String) connection.getMBeanServerConnection().getAttribute(object, "stateName");
        } catch (ReflectionException | InstanceNotFoundException | AttributeNotFoundException | MBeanException e) {
            throw new IllegalStateException();
        }
    }

    // CAUTION: blocks until ctrl-c.
    // Format: https://docs.docker.com/engine/api/v1.33/#operation/ContainerAttach
    public void tailF(Engine engine, PrintWriter dest) throws IOException {
        Collection<String> containers;

        containers = runningContainerMap(engine).keySet();
        if (containers.size() != 1) {
            Server.LOGGER.info("ignoring -tail option because container is not unique");
        } else {
            engine.containerLogsFollow(containers.iterator().next(), new OutputStream() {
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

    public FileNode createContextEatWar(String app, FileNode war) throws IOException {
        FileNode result;

        result = directory.join("context").mkdirOpt().join(app);
        try {
            result.mkdir();
        } catch (MkdirException e) {
            throw new ArgumentException("another build for app " + app + " is in progress, try again later");
        }
        war.move(result.join("app.war"));
        return result;
    }

    public void cleanupContext(String app, String tag, int keep) throws IOException {
        FileNode dir;
        List<FileNode> lst;
        FileNode dest;

        dir = directory.join("context");
        dest = dir.join(app + ":" + tag);
        moveAway(dest);
        dir.join(app).move(dest);
        lst = dir.list();
        Collections.sort(lst, new Comparator<FileNode>() {
            @Override
            public int compare(FileNode left, FileNode right) {
                try {
                    return (int) (left.getLastModified() - right.getLastModified());
                } catch (GetLastModifiedException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        while (lst.size() > keep) {
            lst.remove(0).deleteTree();
        }
    }

    private void moveAway(FileNode file) throws IOException {
        int no;
        FileNode away;

        if (file.exists()) {
            for (no = 1; true; no++) {
                away = file.getParent().join(file.getName() + "_" + no);
                if (!away.exists()) {
                    file.move(away);
                    return;
                }
            }
        }
    }
}
