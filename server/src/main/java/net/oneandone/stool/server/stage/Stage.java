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

import net.oneandone.stool.kubernetes.ContainerInfo;
import net.oneandone.stool.kubernetes.OpenShift;
import net.oneandone.stool.kubernetes.Stats;
import net.oneandone.stool.registry.PortusRegistry;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.registry.TagInfo;
import net.oneandone.stool.server.ArgumentException;
import net.oneandone.stool.server.Server;
import net.oneandone.stool.server.configuration.Expire;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.stool.server.logging.AccessLogEntry;
import net.oneandone.stool.server.util.Context;
import net.oneandone.stool.server.util.Field;
import net.oneandone.stool.server.util.Info;
import net.oneandone.stool.server.util.Property;
import net.oneandone.sushi.fs.FileNotFoundException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarHeader;
import org.kamranzafar.jtar.TarOutputStream;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * A collection of images. From a Docker perspective, a stage roughly represents a Repository.
 * A short-lived object, created for one request, discarded afterwards - caches results for performance.
 */
public class Stage {
    private static final String PROPERTY_NOTIFY = "stageNotify";
    private static final String PROPERTY_EXPIRE = "stageExpire";
    private static final String PROPERTY_COMMENT = "stageComment";

    private static final String NOTIFY_CREATED_BY = "@created-by";
    private static final String NOTIFY_LAST_MODIFIED_BY = "@last-modified-by";

    //--

    private static final String KEEP_IMAGE = "marker string to indicate an 'empty publish'";

    public static final String DEPLOYMENT_LABEL_STAGE = "net.oneandone.stool-stage";

    public static final String MAIN_CONTAINER = "main"; // TODO ...

    //--

    public final Server server;

    /**
     * Has a very strict syntax, it's used:
     * * in Kubernetes resource names
     * * Docker repository tags
     * * label values
     */
    private final String name;

    public Stage(Server server, String name) {
        this.server = server;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String dnsLabel() {
        // is not allowed to contain dots
        return name.replace(".", "--");
    }

    //--

    public FileNode getLogs() {
        return server.getStageLogs(name);
    }

    public String getImage(Engine engine) throws IOException {
        return (String) engine.helmReadValues(name).get("image");
    }

    public String getRepositoryPath(Engine engine) throws IOException {
        return getRepositoryPath(toRepository(getImage(engine)));
    }

    // without hostname
    public static String getRepositoryPath(String repository) {
        String path;

        path = URI.create(repository).getPath();
        path = path.substring(path.indexOf('/') + 1);
        return path;
    }

    public static String getTag(String image) {
        return image.substring(image.lastIndexOf(':') + 1);
    }

    public Registry createRegistry(World world) throws IOException {
        String registry;

        registry = server.configuration.registryUrl();
        /* TODO: re-enabled this test
        URI url;
        String repository;
        url = URI.create(registry);
        repository = getRepository();
        if (!repository.startsWith(url.getHost() + "/")) {
            throw new IllegalStateException(url.getHost() + " vs " + repository);
        }*/
        return PortusRegistry.create(world, Strings.removeRightOpt(registry, "/"), null);
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

    public Info info(Engine engine, String str) throws IOException {
        Info result;
        List<String> lst;

        result = propertyOpt(engine, str);
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
        for (Property p : properties(engine)) {
            lst.add(p.name());
        }
        throw new ArgumentException(str + ": no such status field or property, choose one of " + lst);
    }

    public List<Property> properties(Engine engine) throws IOException {
        Map<String, Object> values;
        List<Property> result;

        values = engine.helmReadValues(name);
        result = new ArrayList<>();
        result.add(new Property(PROPERTY_COMMENT, "", values));
        result.add(new Property(PROPERTY_EXPIRE, Expire.fromNumber(server.configuration.defaultExpire).toString(), values));
        result.add(new Property(PROPERTY_NOTIFY, Stage.NOTIFY_CREATED_BY, values));
        return result;
    }

    public Property property(Engine engine, String property) throws IOException {
        Property result;

        result = propertyOpt(engine, property);
        if (result == null) {
            throw new ArgumentException("unknown property: " + property);
        }
        return result;
    }

    public Property propertyOpt(Engine engine, String property) throws IOException {
        for (Property candidate : properties(engine)) {
            if (property.equals(candidate.name())) {
                return candidate;
            }
        }
        return null;
    }

    public Expire getPropertyExpire(Engine engine) throws IOException {
        return Expire.fromHuman(property(engine, PROPERTY_EXPIRE).get());
    }

    public List<String> getPropertyNotify(Engine engine) throws IOException {
        return Separator.COMMA.split(property(engine, PROPERTY_NOTIFY).get());
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
        fields.add(new Field("values") {
            @Override
            public Object get(Context context) throws IOException {
                return context.engine.helmReadValues(name);
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
                return context.urlMap(Stage.this, context.registry(Stage.this));
            }
        });
        return fields;
    }

    private void appFields(List<Field> fields) {
        fields.add(new Field("pod") {
            @Override
            public Object get(Context context) throws IOException {
                PodInfo info;

                info = context.runningPodsFirst(Stage.this);
                return info == null ? null : info.name;
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
                started = context.engine.podStartedAt(current.first.name /* TODO */, MAIN_CONTAINER);
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
                return current == null ? null : context.sizeRw(current.first /* TODO */.containerId(MAIN_CONTAINER));
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
                stats = OpenShift.create().statsOpt(current.first /* TODO */.name, MAIN_CONTAINER);
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
                stats = OpenShift.create().statsOpt(current.first /* TODO */.name, MAIN_CONTAINER);
                if (stats != null) {
                    return stats.memory;
                } else {
                    return "n.a.";
                }
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

    public void publishConfig(Engine engine) throws IOException {
        install(true, engine, KEEP_IMAGE, Collections.emptyMap());
    }

    /** @return logins */
    public Set<String> notifyLogins(Engine engine) throws IOException {
        Set<String> done;
        String login;

        done = new HashSet<>();
        for (String user : getPropertyNotify(engine)) {
            switch (user) {
                case NOTIFY_LAST_MODIFIED_BY:
                    login = lastModifiedBy();
                    break;
                case NOTIFY_CREATED_BY:
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

    /** @return sorted list, oldest first */
    public List<TagInfo> images(Engine engine, Registry registry) throws IOException {
        return images(registry, getRepositoryPath(engine));
    }

    /** @return sorted list, oldest first */
    public static List<TagInfo> images(Registry registry, String repositoryPath) throws IOException {
        List<String> tags;
        List<TagInfo> result;

        result = new ArrayList<>();
        try {
            tags = registry.tags(repositoryPath);
        } catch (net.oneandone.sushi.fs.FileNotFoundException e) {
            return result;
        }
        for (String tag : tags) {
            result.add(registry.info(repositoryPath, tag));
        }
        Collections.sort(result);
        return result;
    }

    public void checkExpired(Engine engine) throws IOException {
        Expire expire;

        expire = getPropertyExpire(engine);
        if (expire.isExpired()) {
            throw new ArgumentException("Stage expired " + expire + ". To start it, you have to adjust the 'expire' date.");
        }
    }

    public static String toRepository(String imageOrRepository) {
        int idx;

        idx = imageOrRepository.indexOf(':');
        return idx == -1 ? imageOrRepository : imageOrRepository.substring(0, idx);
    }

    /** @param imageOrRepositoryX image to publish this particular image; null or repository to publish latest from (current) repository;
     *                  keep to stick with current image. */
    public String install(boolean upgrade, Engine engine, String imageOrRepositoryX, Map<String, String> clientValues) throws IOException {
        World world;
        FileNode tmp;
        TagInfo image;
        FileNode values;
        Map<String, Object> map;
        FileNode src;

        if (imageOrRepositoryX != null && imageOrRepositoryX != KEEP_IMAGE) {
            validateRepository(toRepository(imageOrRepositoryX));
        }
        world = World.create(); // TODO
        tmp = world.getTemp().createTempDirectory();
        values = world.getTemp().createTempFile();

        if (upgrade) {
            map = new HashMap<>(engine.helmReadValues(name));
            // TODO:
            // put values from image again? it might have changed ...
        } else {
            map = new HashMap<>(server.configuration.values);
        }
        image = resolve(engine, world, imageOrRepositoryX, (String) map.get("image"));
        src = world.file("/etc/charts").join(image.chart);
        if (!src.isDirectory()) {
            throw new ArgumentException("helm chart not found: " + image.chart);
        }
        src.copyDirectory(tmp);
        if (upgrade) {
            // TODO:
            // put values frmo image again? it might have changed ...
        } else {
            map.putAll(image.chartValues);
        }
        map.putAll(clientValues);
        map.put("image", image.repositoryTag);
        map.put("fqdn", stageFqdn());
        map.put("cert", cert());
        map.put("fault", fault(world, image));

        Server.LOGGER.info("values: " + map);
        try (PrintWriter v = new PrintWriter(values.newWriter())) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                v.println(entry.getKey() + ": " + toJson(entry.getValue()));
            }
        }
        try {
            Server.LOGGER.info("helm install upgrade=" + upgrade);
            Server.LOGGER.info(tmp.exec("helm", upgrade ? "upgrade" : "install", "--debug", "--values", values.getAbsolute(), name, tmp.getAbsolute()));
            System.out.println("written values: " + values.readString());
        } finally {
            tmp.deleteTree();
        }
        System.out.println("created values: " + engine.helmReadValues(name));
        engine.deploymentAwait(dnsLabel());
        return image.repositoryTag;
    }

    // this is to avoid engine 500 error reporting "invalid reference format: repository name must be lowercase"
    public static void validateRepository(String image) {
        URI uri;
        int idx;
        String repository;

        idx = image.indexOf(':');
        repository = idx == -1 ? image : image.substring(0, idx);
        if (repository.endsWith("/")) {
            throw new ArgumentException("invalid repository: " + repository);
        }
        try {
            uri = new URI(repository);
        } catch (URISyntaxException e) {
            throw new ArgumentException("invalid repository: " + repository);
        }
        if (uri.getHost() != null) {
            checkLowercase(uri.getHost());
        }
        checkLowercase(uri.getPath());
    }

    private static void checkLowercase(String str) {
        for (int i = 0, length = str.length(); i < length; i++) {
            if (Character.isUpperCase(str.charAt(i))) {
                throw new ArgumentException("invalid registry prefix: " + str);
            }
        }
    }

    private static String toJson(Object obj) {
        if (obj instanceof String) {
            return "\"" + obj + '"';
        } else {
            return obj.toString(); // ok für boolean and integer
        }
    }

    /** tar directory into byte array */
    private String fault(World world, TagInfo image) throws IOException {
        List<String> missing;
        FileNode workspace;
        FileNode project;
        TarOutputStream tar;
        byte[] buffer;
        long now;
        String result;

        missing = new ArrayList<>();
        if (server.configuration.auth()) {
            server.checkFaultPermissions(image.author, image.faultProjects);
        }
        workspace = world.file("/etc/fault/workspace");
        buffer = new byte[64 * 1024];
        try (ByteArrayOutputStream dest = new ByteArrayOutputStream()) {
            tar = new TarOutputStream(new GZIPOutputStream(dest));
            now = System.currentTimeMillis();
            for (String projectName : image.faultProjects) {
                project = workspace.join(projectName);
                if (project.isDirectory()) {
                    faultTarAdd(now, buffer, workspace, project, tar);
                } else {
                    missing.add(projectName);
                }
            }
            tar.close();
            result = Base64.getEncoder().encodeToString(dest.toByteArray());
        }
        if (!missing.isEmpty()) {
            throw new ArgumentException("missing secret directories: " + missing);
        }
        return result;
    }

    /** tar directory into byte array */
    private void faultTarAdd(long now, byte[] buffer, FileNode workspace, FileNode project, TarOutputStream tar) throws IOException {
        List<FileNode> all;
        Iterator<FileNode> iter;
        FileNode file;
        int count;

        all = project.find("**/*");
        iter = all.iterator();
        while (iter.hasNext()) {
            file = iter.next();
            if (file.isDirectory()) {
                tar.putNextEntry(new TarEntry(TarHeader.createHeader(file.getRelative(workspace), 0, now, true, 0700)));
                iter.remove();
            }
        }
        iter = all.iterator();
        while (iter.hasNext()) {
            file = iter.next();
            tar.putNextEntry(new TarEntry(TarHeader.createHeader(file.getRelative(workspace), file.size(), now, false, 0700)));
            try (InputStream src = file.newInputStream()) {
                while (true) {
                    count = src.read(buffer);
                    if (count == -1) {
                        break;
                    }
                    tar.write(buffer, 0, count);
                }
            }
        }
    }

    // TODO: expensive
    private TagInfo resolve(Engine engine, World world, String imageOrRepositoryX, String imagePrevious) throws IOException {
        String imageOrRepository;
        int idx;
        Registry registry;

        if (imageOrRepositoryX == KEEP_IMAGE) {
            imageOrRepository = imagePrevious;
        } else if (imageOrRepositoryX == null) {
            imageOrRepository = toRepository(getImage(engine));
        } else {
            imageOrRepository = imageOrRepositoryX;
        }
        registry = createRegistry(world);
        idx = imageOrRepository.indexOf(':');
        if (idx == -1) {
            return latest(registry, imageOrRepository);
        } else {
            try {
                return tagInfo(registry, imageOrRepository);
            } catch (FileNotFoundException e) {
                throw new ArgumentException("image not found: " + imageOrRepository);
            }
        }
    }

    private static TagInfo latest(Registry registry, String repository) throws IOException {
        String repositoryPath;
        List<TagInfo> all;

        repositoryPath = getRepositoryPath(repository);
        all = images(registry, repositoryPath);
        if (all.isEmpty()) {
            throw new ArgumentException("no image(s) found in repository " + repository);
        }
        return all.get(all.size() - 1);
    }

    private static TagInfo tagInfo(Registry registry, String image) throws IOException {
        String tag;
        String repositoy;

        tag = getTag(image);
        repositoy = getRepositoryPath(toRepository(image));
        return registry.info(repositoy, tag);
    }

    public String uninstall(Engine engine, Registry registry) throws IOException {
        Current current;

        current = currentOpt(engine, registry);
        if (current == null) {
            return null;
        }
        Server.LOGGER.info(World.createMinimal().getWorking().exec("helm", "uninstall", getName()));
        engine.podAwait(current.first /* TODO */.name, null);
        return current.image.tag;
    }

    private String cert() throws IOException {
        FileNode dir;

        dir = server.certificate(stageFqdn());
        return Base64.getEncoder().encodeToString(dir.join("keystore.p12").readBytes());
    }

    //--

    /** @return login name or null if unknown */
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
        pod = runningPodFirst(engine); // first pod is picked as a representative
        if (pod != null) {
            tag = registry.info(pod, MAIN_CONTAINER);
        } else {
            lst = images(engine, registry);
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

    public String stageFqdn() {
        return name + "." + server.configuration.fqdn;
    }

    private List<String> url(TagInfo tag, String protocol) {
        String fqdn;
        String url;
        List<String> result;

        fqdn = stageFqdn();
        url = protocol + "://" + fqdn + "/" + tag.urlContext;
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
        uninstall(engine, registry); // usually returns true for already stopped
    }

    //--

    public static class Current {
        public final TagInfo image;
        public final PodInfo first;

        public Current(TagInfo image, PodInfo first) {
            this.image = image;
            this.first = first;
        }
    }

    /** @return empty list if not running */
    public Map<String, PodInfo> runningPods(Engine engine) throws IOException {
        return engine.podList(Strings.toMap(DEPLOYMENT_LABEL_STAGE, name));
    }

    /** @return null if not running */
    public PodInfo runningPodFirst(Engine engine) throws IOException {
        Map<String, PodInfo> lst;

        lst = runningPods(engine);
        return lst.isEmpty() ? null : lst.values().iterator().next();
    }

    /** @return null if not running */
    public Current currentOpt(Engine engine, Registry registry) throws IOException {
        return currentOpt(registry, runningPods(engine));
    }

    public Current currentOpt(Registry registry, Map<String, PodInfo> runningPods) throws IOException {
        TagInfo image;
        PodInfo first;

        if (runningPods.isEmpty()) {
            return null;
        } else {
            image = null;
            first = null;
            for (PodInfo pod : runningPods.values()) {
                if (image == null) {
                    first = pod;
                    image = registry.info(pod, MAIN_CONTAINER);
                }
                if (!pod.isRunning()) {
                    throw new IllegalStateException("TODO");
                }
            }
            return new Current(image, first);
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
        Stage.Current current;
        ContainerInfo container;

        for (int count = 0; true; count++) {
            current = context.currentOpt(this);
            if (current != null) {
                container = current.first.containers.get(MAIN_CONTAINER);
                if (container != null) {
                    System.out.println("container " + container.id);
                    if (container.ready) {
                        // ok
                        return;
                    }
                }
            }
            if (count > 600) {
                throw new IOException(name + ": waiting for pod timed out");
            }
            if (count % 100 == 99) {
                Server.LOGGER.info(name + ": waiting for pod to get ready ... ");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                // fall-through
            }
        }
    }

    public JMXServiceURL podJmxUrl(Context context, int port) throws IOException {
        PodInfo running;

        running = context.runningPodsFirst(this);
        if (running == null) {
            return null;
        } else {
            // see https://docs.oracle.com/javase/tutorial/jmx/remote/custom.html
            return new JMXServiceURL("service:jmx:jmxmp://" + running.ip + ":" + port);
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

        running = runningPodFirst(engine); // TODO: choose different pod
        if (running == null) {
            Server.LOGGER.info("ignoring -tail option because container is not unique");
        } else {
            engine.podLogsFollow(running.containerId(MAIN_CONTAINER), new OutputStream() {
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
