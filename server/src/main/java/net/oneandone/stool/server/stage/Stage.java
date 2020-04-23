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

import net.oneandone.stool.docker.Daemon;
import net.oneandone.stool.docker.Registry;
import net.oneandone.stool.docker.Stats;
import net.oneandone.stool.server.ArgumentException;
import net.oneandone.stool.server.Server;
import net.oneandone.stool.server.configuration.Accessor;
import net.oneandone.stool.server.configuration.StageConfiguration;
import net.oneandone.stool.docker.ContainerInfo;
import net.oneandone.stool.kubernetes.Data;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.docker.ImageInfo;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.stool.kubernetes.ServiceInfo;
import net.oneandone.stool.server.logging.AccessLogEntry;
import net.oneandone.stool.server.util.Context;
import net.oneandone.stool.server.util.Field;
import net.oneandone.stool.server.util.Info;
import net.oneandone.stool.server.util.Pool;
import net.oneandone.stool.server.util.Ports;
import net.oneandone.stool.server.util.Property;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A collection of images. From a Docker perspective, a stage roughly represents a Repository.
 * A short-lived object, created for one request, discarded afterwards - caches results for performance.
 */
public class Stage {
    private static final String CONTAINER_PREFIX = "net.oneandone.stool-container-";

    public static final String POD_LABEL_REPOSITORY_TAG = CONTAINER_PREFIX + "repo-tag";
    public static final String POD_LABEL_STAGE = CONTAINER_PREFIX + "stage";
    public static final String POD_LABEL_ENV_PREFIX = CONTAINER_PREFIX  + "env.";
    public static final String POD_LABEL_PORT_USED_PREFIX = CONTAINER_PREFIX + "port.";


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
        fields.add(new Field("images") {
            @Override
            public Object get(Context context) throws IOException {
                List<String> result;

                result = new ArrayList<>();
                for (Image image : context.images(Stage.this)) {
                    result.add(image.tag);
                }
                return result;
            }
        });
        fields.add(new Field("running") {
            @Override
            public Object get(Context context) throws IOException {
                Current current;
                List<String> result;

                current = context.currentOpt(Stage.this);
                result = new ArrayList<>();
                if (current != null) {
                    result.add(current.image.tag);
                }
                Collections.sort(result);
                return result;
            }
        });
        appFields(fields);
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
                return context.urlMap(server.pool, Stage.this);
            }
        });
        return fields;
    }

    private void appFields(List<Field> fields) {
        fields.add(new Field("container") {
            @Override
            public Object get(Context context) throws IOException {
                PodInfo info;

                info = context.runningPodOpt(Stage.this);
                return info == null ? null : container(context.docker, info).id;
            }
        });
        fields.add(new Field("uptime") {
            @Override
            public Object get(Context context) throws IOException {
                Current current;
                Long started;

                current = context.currentOpt(Stage.this);
                if (current == null) {
                    return null;
                }
                started = context.engine.podStartedAt(current.pod.name);
                if (started == null) {
                    return null;
                }
                return Stage.timespan(started);
            }
        });
        fields.add(new Field("disk-used") {
            @Override
            public Object get(Context context) throws IOException {
                Current current;

                current = context.currentOpt(Stage.this);
                return current == null ? null : context.sizeRw(current.container);
            }
        });
        fields.add(new Field("cpu") {
            @Override
            public Object get(Context context) throws IOException {
                Current current;
                Stats stats;

                current = context.currentOpt(Stage.this);
                if (current == null) {
                    return null;
                }
                stats = context.containerStats(current.container.id);
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
            public Object get(Context context) throws IOException {
                Current current;
                Stats stats;

                current = context.currentOpt(Stage.this);
                if (current == null) {
                    return null;
                }
                stats = context.containerStats(current.container.id);
                if (stats != null) {
                    return stats.memoryUsage * 100 / stats.memoryLimit;
                } else {
                    // not started
                    return 0L;
                }
            }
        });
        fields.add(new Field("heap") {
            @Override
            public Object get(Context context) throws IOException {
                Current current;

                current = context.currentOpt(Stage.this);
                return current == null ? null : heap(context, current);
            }
        });
        fields.add(new Field("origin-scm") {
            @Override
            public Object get(Context context) throws IOException {
                Current current;

                current = context.currentOpt(Stage.this);
                return current == null ? null : current.image.originScm;
            }
        });
        fields.add(new Field("debug-port") {
            @Override
            public Object get(Context context) {
                Ports ports;

                ports = server.pool.stageOpt(name);
                return ports != null && ports.debug != -1 ? ports.debug : null;
            }
        });
        fields.add(new Field("jmx-port") {
            @Override
            public Object get(Context context) {
                Ports ports;

                ports = server.pool.stageOpt(name);
                return ports != null && ports.jmxmp != -1 ? (ports.jmxmp + " # "
                        + String.format(server.configuration.jmxUsage, ports.jmxmp)) : null;
            }
        });
        fields.add(new Field("environment") {
            @Override
            public Object get(Context context) throws IOException {
                Current current;

                current = context.currentOpt(Stage.this);
                return current == null ? null : env(current.pod);
            }
        });
    }

    private Map<String, String> env(PodInfo info) {
        Map<String, String> result;
        String key;

        result = new HashMap<>();
        if (info != null) {
            for (Map.Entry<String, String> entry : info.labels.entrySet()) {
                key = entry.getKey();
                if (key.startsWith(Stage.POD_LABEL_ENV_PREFIX)) {
                    result.put(key.substring(Stage.POD_LABEL_ENV_PREFIX.length()), entry.getValue());
                }
            }
        }
        return result;
    }

    public String heap(Context context, Stage.Current current) throws IOException {
        JMXServiceURL url;
        MBeanServerConnection connection;
        ObjectName objectName;
        CompositeData result;
        long used;
        long max;

        if (current.container == null) {
            return "";
        }
        if (current.image.ports.jmxmp == -1) {
            return "[no jmx port]";
        }

        url = clusterJmxUrl(context);
        try {
            objectName = new ObjectName("java.lang:type=Memory");
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException(e);
        }
        try (JMXConnector raw = JMXConnectorFactory.connect(url, null)) {
            connection = raw.getMBeanServerConnection();
            try {
                result = (CompositeData) connection.getAttribute(objectName, "HeapMemoryUsage");
            } catch (Exception e) {
                return "[cannot get jmx attribute: " + e.getMessage() + "]";
            }
        } catch (IOException e) {
            Server.LOGGER.debug("cannot connect to jmx server", e);
            return "[cannot connect jmx server: " + e.getMessage() + "]";
        }
        used = (Long) result.get("used");
        max = (Long) result.get("max");
        return Float.toString(((float) (used * 1000 / max)) / 10);
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

    public void wipeImages(Daemon docker, Registry registry) throws IOException {
        for (String repositoryTag : imageTags(registry)) {
            Server.LOGGER.debug("remove image: " + repositoryTag);
            docker.imageRemove(repositoryTag, false);
        }
    }

    /** @return list of tags belonging to this stage */
    private List<String> imageTags(Registry registry) throws IOException {
        return imageTags(registry.imageList());
    }

    /** @return list of repositoryTags belonging to this stage */
    private List<String> imageTags(Map<String, ImageInfo> imageMap) {
        ImageInfo info;
        List<String> result;

        result = new ArrayList<>();
        for (Map.Entry<String, ImageInfo> entry : imageMap.entrySet()) {
            info = entry.getValue();
            for (String repositoryTag : info.repositoryTags) {
                if (repositoryTag.startsWith(server.configuration.registryNamespace + "/" + name + ":")) {
                    result.add(repositoryTag);
                }
            }
        }
        return result;
    }

    /** @return sorted list */
    public List<Image> images(Registry registry) throws IOException {
        return images(registry, registry.imageList());
    }

    /** @return sorted list */
    public List<Image> images(Registry registry, Map<String, ImageInfo> imageMap) throws IOException {
        List<Image> result;
        Map<String, Image> all;
        Image image;

        result = new ArrayList<>();
        for (String repositoryTag : imageTags(imageMap)) {
            all = Image.loadAll(registry, repositoryTag);
            image = all.get(repositoryTag);
            if (image == null) {
                throw new IllegalStateException("TODO: " + repositoryTag + " " + all.keySet());
            }
            result.add(image);
        }
        Collections.sort(result);
        return result;
    }

    public void checkExpired() {
        if (configuration.expire.isExpired()) {
            throw new ArgumentException("Stage expired " + configuration.expire + ". To start it, you have to adjust the 'expire' date.");
        }
    }

    // --storage-opt size=42m could limit disk space, but it's only available for certain storage drivers (with certain mount options) ...
    public void checkDiskQuota(Engine engine, Daemon docker, Registry registry) throws IOException {
        int used;
        int quota;
        Current current;
        ContainerInfo info;

        current = currentOpt(engine, docker, registry);
        if (current != null) {
            info = current.container;
            if (info != null) {
                used = new Context(engine, docker, registry).sizeRw(info);
                quota = current.image.disk;
                if (used > quota) {
                    throw new ArgumentException("Stage disk quota exceeded. Used: " + used + " mb  >  quota: " + quota + " mb.\n");
                }
            }
        }
    }

    private String podName() {
        return name.replace('.', '-');
    }

    private void wipeResources(Engine engine, Daemon docker) throws IOException {
        String podName;
        String httpService;
        String httpsService;

        podName = podName();
        httpService = podName + "http";
        httpsService = podName + "https";
        if (engine.serviceGetOpt(httpService) != null) {
            Server.LOGGER.debug("wipe kubernetes resources");
            if (engine.podProbe(podName) != null) {
                podDelete(engine, docker, podName);
            }
            engine.serviceDelete(httpService);
            if (engine.serviceGetOpt(httpsService) != null) {
                engine.serviceDelete(httpsService);
            }
            engine.serviceDelete(jmxServiceName());
            try {
                engine.secretDelete(podName);
            } catch (FileNotFoundException e) {
                // ok
            }
            try {
                engine.configMapDelete(podName);
            } catch (FileNotFoundException e) {
                // ok
            }
        }
    }

    /** @return image actually started, null if this image is actually running
     *  @throws IOException if a different image is already running */
    public String start(Engine engine, Daemon docker, Registry registry, Pool pool, String imageOpt, int http,
                        int https, Map<String, String> clientEnvironment)
            throws IOException {
        String podName;
        PodInfo running;
        Daemon.Status status;
        Ports hostPorts;
        Map<String, String> environment;
        Map<FileNode, String> mounts;
        List<Data> dataList;
        Data cert;
        Data fault;
        Map<String, String> labels;
        int memoryQuota;
        int memoryReserved;
        Image image;

        podName = podName();
        server.sshDirectory.update(); // ports may change - make sure to wipe outdated keys
        memoryReserved = server.memoryReservedContainers(engine, registry);
        memoryQuota = server.configuration.memoryQuota;
        image = resolve(registry, imageOpt);
        running = runningPodOpt(engine);
        if (running != null) {
            if (image.repositoryTag.equals(running.repositoryTag)) {
                return null;
            } else {
                throw new IOException("conflict: cannot start image " + image.tag
                        + " because a different image id " + image.repositoryTag + " " + running.repositoryTag + " is already running");
            }
        }
        if (memoryQuota != 0 && memoryReserved + image.memory > memoryQuota) {
            throw new ArgumentException("Cannot reserve memory for stage " + name + " :\n"
                    + "  unreserved: " + (memoryQuota - memoryReserved) + "\n"
                    + "  requested: " + image.memory + "\n"
                    + "Consider stopping stages.");
        }
        memoryReserved += image.memory; // TODO
        wipeResources(engine, docker);
        environment = new HashMap<>(server.configuration.environment);
        environment.putAll(configuration.environment);
        environment.putAll(clientEnvironment);
        Server.LOGGER.debug("environment: " + environment);
        Server.LOGGER.info(name + ": starting container ... ");
        mounts = logMount();
        hostPorts = pool.allocate(this, http, https);
        labels = new HashMap<>();
        labels.put(POD_LABEL_STAGE, name);
        labels.put(POD_LABEL_REPOSITORY_TAG, Engine.encodeLabel(image.repositoryTag));
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            labels.put(POD_LABEL_ENV_PREFIX + entry.getKey(), entry.getValue());
        }

        dataList = new ArrayList<>();
        cert = certMountOpt(image);
        if (cert != null) {
            dataList.add(cert);
            cert.define(engine);
        }
        fault = faultDataOpt(image);
        if (fault != null) {
            dataList.add(fault);
            fault.define(engine);
        }
        engine.serviceCreate(podName + "http", hostPorts.http, image.ports.http,
                Strings.toMap(POD_LABEL_STAGE, name), httpServiceLabels(hostPorts));
        if (hostPorts.https != -1) {
            engine.serviceCreate(podName + "https", hostPorts.https, image.ports.https,
                    Strings.toMap(POD_LABEL_STAGE, name), httpServiceLabels(hostPorts));
        }
        engine.serviceCreate(jmxServiceName(), hostPorts.jmxmp, image.ports.jmxmp, POD_LABEL_STAGE, name);
        if (!engine.podCreate(podName, image.repositoryTag,
                "h" /* TODO */ + md5(getName()) /* TODO + "." + server.configuration.dockerHost */,
                false, 1024 * 1024 * image.memory, labels, environment, mounts, dataList)) {
            throw new IOException("pod already terminated: " + name);
        }
        Server.LOGGER.debug("created pod " + podName);
        status = engine.podContainerStatus(podName);
        if (status != Daemon.Status.RUNNING) {
            throw new IOException("unexpected status: " + status);
        }
        return image.tag;
    }

    private Map<String, String> httpServiceLabels(Ports hostPorts) {
        Map<String, String> result;

        result = hostPorts.toUsedLabels();
        result.put(POD_LABEL_STAGE, name);
        return result;
    }

    public String jmxServiceName() {
        return podName() + "jmxmp";
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

    private Image resolve(Registry registry, String imageOpt) throws IOException {
        List<Image> all;
        Image image;

        all = images(registry);
        if (all.isEmpty()) {
            throw new ArgumentException("no image to start - did you build the stage?");
        }
        if (imageOpt == null) {
            image = all.get(all.size() - 1);
        } else {
            image = lookup(all, imageOpt);
            if (image == null) {
                throw new ArgumentException("image not found: " + imageOpt);
            }
        }
        return image;
    }

    private static Image lookup(List<Image> images, String tag) {
        for (Image image : images) {
            if (image.tag.equals(tag)) {
                return image;
            }
        }
        return null;
    }

    /** @return tag actually stopped, or null if already stopped */
    public String stop(Engine engine, Daemon docker, Registry registry) throws IOException {
        Current current;

        server.sshDirectory.update(); // ports may change - make sure to wipe outdated keys
        current = currentOpt(engine, docker, registry);
        if (current == null) {
            return null;
        }
        Server.LOGGER.info(current.image.tag + ": deleting pod ...");
        podDelete(engine, docker, podName()); // TODO: timeout 5 minutes
        return current.image.tag;
    }

    // TODO
    public static void podDelete(Engine engine, Daemon docker, String podName) throws IOException {
        String container;

        container = engine.podDelete(podName);
        try {
            docker.containerRemove(container);
        } catch (net.oneandone.sushi.fs.FileNotFoundException e) {
            // fall-through, already deleted
        }
        // TODO: what if there's more than one container for this pod?
    }

    private Map<FileNode, String> logMount() throws IOException {
        FileNode hostLogRoot;
        Map<FileNode, String> result;

        hostLogRoot = server.serverHome.join("stages", getName(), "logs");
        // same as hostLogRoot, but the path as needed inside the server:
        logs().mkdirsOpt();
        result = new HashMap<>();
        result.put(hostLogRoot, "/var/log/stool");
        return result;
    }

    private Data certMountOpt(Image image) throws IOException {
        FileNode file;
        Data result;
        int idx;

        if (image.ports.https == -1 || image.p12 == null) {
            return null;
        }
        file = server.certificate(server.configuration.vhosts
                ? getName() + "." + server.configuration.dockerHost : server.configuration.dockerHost);
        idx = image.p12.lastIndexOf('/');
        if (idx == -1) {
            throw new IllegalArgumentException(image.p12);
        }
        result = Data.configMap(podName(), image.p12.substring(0, idx), true);
        result.add(image.p12.substring(idx + 1), file.readBytes());
        return result;
    }

    private Data faultDataOpt(Image image) throws IOException {
        Data result;
        List<String> missing;
        FileNode innerRoot;
        FileNode innerFile;
        FileNode outerFile;

        if (image.faultProjects.isEmpty()) {
            return null;
        }

        // same as hostLogRoot, but the path as needed inside the server:
        logs().mkdirsOpt();
        result = Data.secrets(podName(), "/root/.fault");
        missing = new ArrayList<>();
        if (server.configuration.auth()) {
            server.checkFaultPermissions(image.createdBy, image.faultProjects);
        }
        innerRoot = directory.getWorld().file("/etc/fault/workspace");
        for (String project : image.faultProjects) {
            innerFile = innerRoot.join(project);
            outerFile = server.secrets.join(project);
            if (innerFile.isDirectory()) {
                result.addDirectory(innerRoot, innerFile);
            } else {
                missing.add(outerFile.getAbsolute());
            }
        }
        if (!missing.isEmpty()) {
            throw new ArgumentException("missing secret directories: " + missing);
        }
        return result;
    }

    //--

    /** @return login name or null if unkonwn */
    public String createdBy() throws IOException {
        AccessLogEntry entry;

        entry = oldest(accessLogModifiedOnly());
        return entry == null ? null : entry.user;
    }

    //--

    public Map<String, String> urlMap(Engine engine, Daemon docker, Registry registry, Pool pool) throws IOException {
        return urlMap(docker, registry, pool, allPodMap(engine).values());
    }

    /**
     * @return empty map if no ports are allocated
     */
    public Map<String, String> urlMap(Daemon docker, Registry registry, Pool pool, Collection<PodInfo> allPodList) throws IOException {
        Map<String, String> result;
        Ports ports;
        Image image;

        result = new LinkedHashMap<>();
        image = null;
        for (PodInfo pod : allPodList) {
            if (name.equals(pod.labels.get(Stage.POD_LABEL_STAGE))) {
                image = Image.load(registry, pod, pod.repositoryTag);
            }
        }
        ports = pool.stageOpt(name);
        if (ports != null) {
            addUrlMap(image, ports, result);
       }
        return result;
    }

    private void addUrlMap(Image image, Ports ports, Map<String, String> dest) {
        if (image == null) {
            throw new IllegalStateException("no image for stage " + name);
        }
        if (ports.http != -1) {
            addNamed("http", url(image, "http", ports.http), dest);
        }
        if (ports.https != -1) {
            addNamed("https", url(image, "https", ports.https), dest);
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
            hostname = getName() + "." + hostname;
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

    public void remove(Engine engine, Daemon docker, Registry registry) throws IOException {
        wipeResources(engine, docker);
        wipeImages(docker, registry);
        server.pool.remove(name);
        getDirectory().deleteTree();
    }

    //--

    public static class Current {
        public final Image image;
        public final PodInfo pod;
        public final ContainerInfo container;

        public Current(Image image, PodInfo pod, ContainerInfo container) {
            this.image = image;
            this.pod = pod;
            this.container = container;
        }
    }

    // TODO: filter query
    // not just this stage
    // @return all stage pods
    public static Map<String, PodInfo> allPodMap(Engine engine) throws IOException {
        Map<String, PodInfo> result;
        Map<String, PodInfo> clone;

        result = engine.podList(); // TODO: filter none-stool pods
        clone = new HashMap<>(result);
        for (Map.Entry<String, PodInfo> entry : clone.entrySet()) {
            if (!entry.getValue().labels.containsKey(POD_LABEL_STAGE)) {
                result.remove(entry.getKey());
            }
        }
        return result;
    }

    // not just this stage
    public static ContainerInfo container(Daemon docker, PodInfo pod) throws IOException {
        String container;

        container = pod.containerId;
        if (container == null) {
            throw new IllegalStateException("TODO");
        }
        return docker.containerInfo(container);
    }

    public PodInfo runningPodOpt(Map<String, PodInfo> allPodMap) {
        PodInfo result;
        PodInfo pod;

        result = null;
        for (Map.Entry<String, PodInfo> entry : allPodMap.entrySet()) {
            pod = entry.getValue();
            if (name.equals(pod.labels.get(POD_LABEL_STAGE)) && pod.isRunning()) {
                if (result != null) {
                    throw new IllegalStateException();
                }
                result = pod;
            }
        }
        return result;
    }

    /** @return null if not running */
    public PodInfo runningPodOpt(Engine engine) throws IOException {
        PodInfo result;

        result = null;
        for (PodInfo pod : allPodMap(engine).values()) { // TODO: expensive
            if (name.equals(pod.labels.get(POD_LABEL_STAGE)) && pod.isRunning()) {
                if (result != null) {
                    throw new IllegalStateException(result.toString());
                }
                result = pod;
            }
        }
        return result;
    }

    /** @return null if not running */
    public Current currentOpt(Engine engine, Daemon docker, Registry registry) throws IOException {
        return currentOpt(registry, docker, runningPodOpt(engine));
    }

    public Current currentOpt(Registry registry, Daemon docker, PodInfo runningPodOpt) throws IOException {
        Image image;
        ContainerInfo container;

        if (runningPodOpt != null) {
            container = container(docker, runningPodOpt);
            image = Image.load(registry, runningPodOpt, container.imageId);
            return new Current(image, runningPodOpt, container);
        } else {
            return null;
        }
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

    public void awaitStartup(Context context) throws IOException {
        JMXServiceURL url;
        String state;

        url = clusterJmxUrl(context);
        if (url != null) {
            for (int count = 0; true; count++) {
                try {
                    state = jmxEngineState(url);
                    break;
                } catch (Exception e) {
                    if (count > 600) {
                        throw new IOException(name + ": initial state timed out: " + e.getMessage(), e);
                    }
                    if (count % 100 == 99) {
                        Server.LOGGER.info(name + ": waiting for tomcat startup ... ");
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
                    throw new IOException(name + ": tomcat startup timed out, state" + state);
                }
                if (count % 100 == 99) {
                    Server.LOGGER.info(name + ": waiting for tomcat startup ... " + state);
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

    public JMXServiceURL clusterJmxUrl(Context context) throws IOException {
        String ip;
        PodInfo running;
        ServiceInfo service;

        running = context.runningPodOpt(this);
        if (running == null) {
            return null;
        } else {
            service = context.engine.serviceGet(jmxServiceName());
            ip = service.clusterIp;
            // see https://docs.oracle.com/javase/tutorial/jmx/remote/custom.html
            return new JMXServiceURL("service:jmx:jmxmp://" + ip + ":" + service.containerPort);
        }
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
    public void tailF(Engine engine, Daemon docker, PrintWriter dest) throws IOException {
        PodInfo running;

        running = runningPodOpt(engine);
        if (running == null) {
            Server.LOGGER.info("ignoring -tail option because container is not unique");
        } else {
            engine.podLogsFollow(container(docker, running).id, new OutputStream() {
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
