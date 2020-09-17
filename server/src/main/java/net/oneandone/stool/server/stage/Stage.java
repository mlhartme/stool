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

import net.oneandone.stool.kubernetes.DeploymentInfo;
import net.oneandone.stool.kubernetes.OpenShift;
import net.oneandone.stool.kubernetes.Stats;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.registry.TagInfo;
import net.oneandone.stool.server.ArgumentException;
import net.oneandone.stool.server.Server;
import net.oneandone.stool.server.StageExistsException;
import net.oneandone.stool.server.configuration.Accessor;
import net.oneandone.stool.server.configuration.StageConfiguration;
import net.oneandone.stool.kubernetes.Data;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.stool.server.logging.AccessLogEntry;
import net.oneandone.stool.server.util.Context;
import net.oneandone.stool.server.util.Field;
import net.oneandone.stool.server.util.Info;
import net.oneandone.stool.server.util.Ports;
import net.oneandone.stool.server.util.Property;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.StatusException;
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
    private static final String LABEL_PREFIX = "net.oneandone.stool-";

    public static final String DEPLOYMENT_LABEL_STAGE = LABEL_PREFIX + "stage";
    public static final String DEPLOYMENT_LABEL_ENV_PREFIX = LABEL_PREFIX  + "env.";

    //--

    public final Server server;

    /**
     * Has a very strict syntax, it's used:
     * * in Kubernetes resource names
     * * Docker repository tags
     * * label values
     */
    private final String name;

    public final StageConfiguration configuration;

    public Stage(Server server, String name, StageConfiguration configuration) {
        this.server = server;
        this.name = name;
        this.configuration = configuration;
    }

    public String getName() {
        return name;
    }

    //-- kubernetes names

    public String deploymentName() {
        // is not allowed to contain dots
        return name.replace(".", "--");
    }
    private String faultSecretName() {
        return name + "-fault";
    }
    private String certSecretName() {
        return name + "-cert";
    }
    private String fluentdConfigName() {
        return name + "-fluentd";
    }
    public String httpRouteName() {
        return name + "-http";
    }
    public String httpsRouteName() {
        return name + "-https";
    }
    public String appIngressName() {
        return name + "ingress";
    }
    public String appServiceName() {
        // is not allowed to contain dots: https://kubernetes.io/docs/concepts/services-networking/service/
        return name.replace(".", "--");
    }



    public FileNode getLogs() {
        return server.getStageLogs(name);
    }

    public String getRepository() {
        return server.configuration.registryPath() + name;
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
                for (TagInfo image : context.images(Stage.this)) {
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
                return context.urlMap(Stage.this);
            }
        });
        return fields;
    }

    private void appFields(List<Field> fields) {
        fields.add(new Field("container") { // TODO: change to pod
            @Override
            public Object get(Context context) throws IOException {
                PodInfo info;

                info = context.runningPodOpt(Stage.this);
                return info == null ? null : info.containerId;
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
                return current == null ? null : context.sizeRw(current.pod.containerId);
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
                stats = OpenShift.create().statsOpt(current.pod.name);
                if (stats != null) {
                    return stats.cpu;
                } else {
                    return "n.a.";
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
                stats = OpenShift.create().statsOpt(current.pod.name);
                if (stats != null) {
                    return stats.memory;
                } else {
                    return "n.a.";
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
        fields.add(new Field("environment") {
            @Override
            public Object get(Context context) throws IOException {
                return env(context.deploymentOpt(Stage.this));
            }
        });
    }

    private Map<String, String> env(DeploymentInfo info) {
        Map<String, String> result;
        String key;

        result = new HashMap<>();
        if (info != null) {
            for (Map.Entry<String, String> entry : info.labels.entrySet()) {
                key = entry.getKey();
                if (key.startsWith(Stage.DEPLOYMENT_LABEL_ENV_PREFIX)) {
                    result.put(Engine.decodeLabel(key.substring(Stage.DEPLOYMENT_LABEL_ENV_PREFIX.length())),
                            Engine.decodeLabel(entry.getValue()));
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

        if (current.pod.containerId == null) {
            return "";
        }
        if (current.image.ports.jmxmp == -1) {
            return "[no jmx port]";
        }

        url = podJmxUrl(context);
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

    public void saveConfig(Engine engine, boolean overwrite) throws IOException, StageExistsException {
        try {
            configuration.save(server.gson, engine, name, overwrite);
        } catch (StatusException e) {
            if (e.getStatusLine().code == 409) {
                throw new StageExistsException();
            }
        }
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

    public void wipeImages(Registry registry) throws IOException {
        registry.delete(getRepository());
    }

    /** @return sorted list, oldest first */
    public List<TagInfo> images(Registry registry) throws IOException {
        List<String> tags;
        List<TagInfo> result;

        result = new ArrayList<>();
        try {
            tags = registry.tags(getRepository());
        } catch (net.oneandone.sushi.fs.FileNotFoundException e) {
            return result;
        }
        System.out.println(getRepository() + " tags: " + tags);
        for (String tag : tags) {
            result.add(registry.info(getRepository(), tag));
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
    public void checkDiskQuota(Engine engine, Registry registry) throws IOException {
        int used;
        int quota;
        Current current;
        String containerId;

        current = currentOpt(engine, registry);
        if (current != null) {
            containerId = current.pod.containerId;
            if (containerId != null) {
                used = new Context(engine, registry).sizeRw(containerId);
                quota = current.image.disk;
                if (used > quota) {
                    throw new ArgumentException("Stage disk quota exceeded. Used: " + used + " mb  >  quota: " + quota + " mb.\n");
                }
            }
        }
    }

    private void wipeStartedResources(Engine engine) throws IOException {
        String deploymentName;
        String serviceName;

        serviceName = appServiceName();
        if (engine.serviceGetOpt(serviceName) != null) {
            Server.LOGGER.debug("wipe kubernetes resources");
            deploymentName = deploymentName();
            if (engine.deploymentProbe(deploymentName) != null) {
                engine.deploymentDelete(deploymentName);
            }
            engine.serviceDelete(serviceName);
            if (server.openShift) {
                try {
                    OpenShift.create().routeDelete(httpRouteName());
                } catch (Exception e) {
                    // TODO
                    System.out.println("todo: http route delete failed: " + e.getMessage());
                }
                try {
                    OpenShift.create().routeDelete(httpsRouteName());
                } catch (Exception e) {
                    // TODO
                    System.out.println("todo: https route delete failed: " + e.getMessage());
                }
            } else {
                engine.ingressDelete(appIngressName());
            }
            try {
                engine.secretDelete(faultSecretName());
            } catch (FileNotFoundException e) {
                // ok
            }
            try {
                engine.secretDelete(certSecretName());
            } catch (FileNotFoundException e) {
                // ok
            }
        }
    }

    /** @return image actually started, null if this image is actually running
     *  @throws IOException if a different image is already running */
    public String start(Engine engine, Registry registry, String imageOpt, Map<String, String> clientEnvironment) throws IOException {
        String deploymentName;
        PodInfo running;
        Map<String, String> environment;
        List<Data> datas;
        Map<String, Data> mounts;
        Map<String, String> deploymentLabels;
        Map<String, String> podLabels;
        int memoryQuota;
        int memoryReserved;
        TagInfo image;

        deploymentName = deploymentName();
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
        wipeStartedResources(engine);
        environment = new HashMap<>(server.configuration.environment);
        environment.putAll(configuration.environment);
        environment.putAll(clientEnvironment);
        Server.LOGGER.debug("environment: " + environment);
        Server.LOGGER.info(name + ": starting container ... ");
        deploymentLabels = new HashMap<>();
        deploymentLabels.put(DEPLOYMENT_LABEL_STAGE, name);
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            deploymentLabels.put(DEPLOYMENT_LABEL_ENV_PREFIX + Engine.encodeLabel(entry.getKey()), Engine.encodeLabel(entry.getValue()));
        }

        podLabels = new HashMap<>();
        podLabels.put(DEPLOYMENT_LABEL_STAGE, name);
        if (image.ports.jmxmp != -1) {
            podLabels.put(Ports.Port.JMXMP.label(), "x" + image.ports.jmxmp);
        }

        datas = new ArrayList<>();
        mounts = new HashMap<>();
        certMount(image, engine, datas, mounts);
        faultMount(image, engine, datas, mounts);

        appService(engine, image);
        if (server.openShift) {
            if (image.ports.http != -1) {
                OpenShift.create().routeCreate(httpRouteName(), stageHost(), appServiceName(), false, "http");
            }
            if (image.ports.https != -1) {
                OpenShift.create().routeCreate(httpsRouteName(), stageHost(), appServiceName(), true, "https");
            }
        } else {
            // TODO: does not map both ports ...
            engine.ingressCreate(appIngressName(), stageHost(), appServiceName(), Ports.HTTP);
        }
        engine.deploymentCreate(deploymentName, Strings.toMap(DEPLOYMENT_LABEL_STAGE, name), deploymentLabels,
                new Engine.Container("main", image.repositoryTag, null, true, environment, 1 /* TODO */, 1024 * 1024 * image.memory, mounts),
                "h" /* TODO */ + md5(getName()) /* TODO + "." + server.configuration.host */,
                podLabels, datas);

        Server.LOGGER.debug("created deployment " + deploymentName);

        return image.tag;
    }

    private void appService(Engine engine, TagInfo image) throws IOException {
        List<String> names;
        List<Integer> src;
        List<Integer> dest;

        names = new ArrayList<>();
        src = new ArrayList<>();
        dest = new ArrayList<>();
        if (image.ports.http != -1) {
            names.add("http");
            src.add(Ports.HTTP);
            dest.add(image.ports.http);
        }
        if (image.ports.https != -1) {
            names.add("https");
            src.add(Ports.HTTPS);
            dest.add(image.ports.https);
        }
        if (names.isEmpty()) {
            throw new IOException("neither http nor https specified");
        }
        engine.serviceCreate(appServiceName(), names, src, dest,
                Strings.toMap(DEPLOYMENT_LABEL_STAGE, name), Strings.toMap(DEPLOYMENT_LABEL_STAGE, name));
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

    private TagInfo resolve(Registry registry, String imageOpt) throws IOException {
        List<TagInfo> all;
        TagInfo image;

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

    private static TagInfo lookup(List<TagInfo> images, String tag) {
        for (TagInfo image : images) {
            if (image.tag.equals(tag)) {
                return image;
            }
        }
        return null;
    }

    /** @return tag actually stopped, or null if already stopped */
    public String stop(Engine engine, Registry registry) throws IOException {
        Current current;
        PodInfo pod;

        current = currentOpt(engine, registry);
        if (current == null) {
            return null;
        }
        Server.LOGGER.info(current.image.tag + ": deleting deployment ...");
        pod = runningPodOpt(engine);
        if (pod == null) {
            throw new IllegalStateException();
        }
        engine.deploymentDelete(deploymentName());
        engine.podAwait(pod.name, null);
        return current.image.tag;
    }

    private void certMount(TagInfo image, Engine engine, List<Data> dataList, Map<String, Data> mounts) throws IOException {
        Data cert;
        String mountPath;
        FileNode dir;

        mountPath = prefix(image.certificateP12, image.certificateChain, image.certificateKey);
        if (image.ports.https == -1 || mountPath == null) {
            return;
        }
        if (mountPath.isEmpty()) {
            throw new ArgumentException("no common prefix: "
                    + image.certificateP12 + " " + image.certificateChain + " " + image.certificateKey);
        }
        mountPath = Strings.removeRight(mountPath, "/");
        System.out.println("prefix: " + mountPath);
        dir = server.certificate(stageHost());
        // CAUTION: that's a config map, and not a secret, because I use sub paths
        cert = Data.secrets(certSecretName(), true);
        if (image.certificateKey != null) {
            cert.addRelative(dir.join("key.pem"), mountPath, image.certificateKey);
        }
        if (image.certificateChain != null) {
            cert.addRelative(dir.join("chain.pem"), mountPath, image.certificateChain);
        }
        if (image.certificateP12 != null) {
            cert.addRelative(dir.join("keystore.p12"), mountPath, image.certificateP12);
        }
        dataList.add(cert);
        cert.define(engine);
        mounts.put(mountPath, cert);
    }

    private static String prefix(String... paths) {
        String result;
        int idx;

        result = null;
        for (String path : paths) {
            if (path == null) {
                continue;
            }
            if (result == null) {
                idx = path.lastIndexOf('/');
                if (idx == -1) {
                    throw new IllegalArgumentException(path);
                }
                result = path.substring(0, idx + 1);
            } else {
                result = common(result, path);
            }
        }
        return result;
    }

    private static String common(String first, String second) {
        int idx;

        if (second.startsWith(first)) {
            return first;
        }
        idx = first.lastIndexOf('/');
        return idx == -1 ? "" : common(first.substring(0, idx + 1), second);
    }

    private void faultMount(TagInfo image, Engine engine, List<Data> datas, Map<String, Data> mounts) throws IOException {
        Data fault;
        List<String> missing;
        FileNode innerRoot;
        FileNode innerFile;

        if (image.faultProjects.isEmpty()) {
            return;
        }

        // same as hostLogRoot, but the path as needed inside the server:
        fault = Data.secrets(faultSecretName(), false);
        missing = new ArrayList<>();
        if (server.configuration.auth()) {
            server.checkFaultPermissions(image.author, image.faultProjects);
        }
        innerRoot = server.getServerLogs().getWorld().file("/etc/fault/workspace");
        for (String project : image.faultProjects) {
            innerFile = innerRoot.join(project);
            if (innerFile.isDirectory()) {
                fault.addDirectory(innerRoot, innerFile);
            } else {
                missing.add(innerFile.getAbsolute());
            }
        }
        if (!missing.isEmpty()) {
            throw new ArgumentException("missing secret directories: " + missing);
        }
        datas.add(fault);
        mounts.put("/root/.fault", fault);
        fault.define(engine);
    }

    //--

    /** @return login name or null if unkonwn */
    public String createdBy() throws IOException {
        AccessLogEntry entry;

        entry = oldest(accessLogModifiedOnly());
        return entry == null ? null : entry.user;
    }

    //--

    /**
     * @return empty map if no ports are allocated
     */
    public Map<String, String> urlMap(Engine engine, Registry registry) throws IOException {
        Map<String, String> result;
        PodInfo pod;
        TagInfo tag;
        List<TagInfo> lst;

        result = new LinkedHashMap<>();
        pod = runningPodOpt(engine);
        if (pod != null) {
            tag = registry.info(pod);
        } else {
            lst = images(registry);
            tag = lst.isEmpty() ? null : lst.get(lst.size() - 1);
        }
        if (tag != null) {
            addNamed("http", url(tag, "http"), result);
            addNamed("https", url(tag, "https"), result);
        }
        return result;
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

    public String stageHost() {
        return name + "." + server.configuration.host;
    }

    private List<String> url(TagInfo tag, String protocol) {
        String hostname;
        String url;
        List<String> result;

        hostname = stageHost();
        url = protocol + "://" + hostname + "/" + tag.urlContext;
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        result = new ArrayList<>();
        for (String suffix : tag.urlSuffixes) {
            result.add(url + suffix);
        }
        return result;
    }

    public void delete(Engine engine, Registry registry) throws IOException {
        StageConfiguration.delete(engine, name);
        wipeStartedResources(engine);
        wipeImages(registry);
    }

    //--

    public static class Current {
        public final TagInfo image;
        public final PodInfo pod;

        public Current(TagInfo image, PodInfo pod) {
            this.image = image;
            this.pod = pod;
        }
    }

    /** @return null if not running */
    public PodInfo runningPodOpt(Engine engine) throws IOException {
        Map<String, PodInfo> lst;

        lst = engine.podList(Strings.toMap(DEPLOYMENT_LABEL_STAGE, name));
        switch (lst.size()) {
            case 0:
                return null;
            case 1:
                PodInfo result;

                result = lst.values().iterator().next();
                return result.isRunning() ? result : null;
            default:
                throw new IOException(lst.toString());
        }
    }

    /** @return null if not running */
    public Current currentOpt(Engine engine, Registry registry) throws IOException {
        return currentOpt(registry, runningPodOpt(engine));
    }

    public Current currentOpt(Registry registry, PodInfo runningPodOpt) throws IOException {
        TagInfo image;

        if (runningPodOpt != null) {
            if (runningPodOpt.containerId == null) {
                throw new IllegalStateException("TODO");
            }
            image = registry.info(runningPodOpt);
            return new Current(image, runningPodOpt);
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

        url = podJmxUrl(context);
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

    public JMXServiceURL podJmxUrl(Context context) throws IOException {
        PodInfo running;
        int port;
        String str;

        running = context.runningPodOpt(this);
        if (running == null) {
            return null;
        } else {
            str = running.labels.get(Ports.Port.JMXMP.label());
            if (str == null) {
                return null;
            } else {
                port = Integer.parseInt(Strings.removeLeft(str, "x"));
                // see https://docs.oracle.com/javase/tutorial/jmx/remote/custom.html
                return new JMXServiceURL("service:jmx:jmxmp://" + running.ip + ":" + port);
            }
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
    public void tailF(Engine engine, PrintWriter dest) throws IOException {
        PodInfo running;

        running = runningPodOpt(engine);
        if (running == null) {
            Server.LOGGER.info("ignoring -tail option because container is not unique");
        } else {
            engine.podLogsFollow(running.containerId, new OutputStream() {
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
