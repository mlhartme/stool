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
package net.oneandone.stool.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.oneandone.stool.kubernetes.OpenShift;
import net.oneandone.stool.kubernetes.Stats;
import net.oneandone.stool.registry.PortusRegistry;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.registry.TagInfo;
import net.oneandone.stool.server.settings.Expire;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.stool.server.logging.AccessLogEntry;
import net.oneandone.stool.server.settings.Settings;
import net.oneandone.stool.server.util.Context;
import net.oneandone.stool.server.util.Field;
import net.oneandone.stool.server.util.Info;
import net.oneandone.stool.server.util.Value;
import net.oneandone.stool.server.values.Application;
import net.oneandone.stool.server.values.Expressions;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A short-lived object, created for one request, discarded afterwards - caches results for performance.
 */
public class Stage {
    public static Stage create(Server server, String name, String image, Map<String, String> values) throws IOException {
        Stage stage;

        stage = new Stage(server, name);
        // TODO: no values available yet ...
        //  stage.checkExpired(engine);
        stage.install(false, new HashMap<>(), image, values);
        return stage;
    }

    //--

    public static final String NOTIFY_FIRST_MODIFIER = "@first";
    public static final String NOTIFY_LAST_MODIFIER = "@last";

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

    //-- values

    private JsonObject lazyHelmObject = null;

    private JsonObject helmObject(Engine engine) throws IOException {
        if (lazyHelmObject == null) {
            lazyHelmObject = engine.helmRead(name);
        }
        return lazyHelmObject;
    }

    private Map<String, Object> helmValues(Engine engine) throws IOException {
        JsonObject helmObject;
        Map<String, Object> result;

        helmObject = helmObject(engine);
        result = toStringMap(helmObject.get("chart").getAsJsonObject().get("values").getAsJsonObject());
        result.putAll(toStringMap(helmObject.get("config").getAsJsonObject()));
        return result;
    }

    public Map<String, Value> values(Engine engine) throws IOException {
        Map<String, Value> result;

        result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : helmValues(engine).entrySet()) {
            result.put(entry.getKey(), new Value(entry.getKey(), entry.getValue().toString()));
        }
        return result;
    }

    private static Map<String, Object> toStringMap(JsonObject obj) {
        Map<String, Object> result;
        JsonPrimitive value;
        Object v;

        result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            value = entry.getValue().getAsJsonPrimitive();
            if (value.isNumber()) {
                v = value.getAsInt();
            } else if (value.isBoolean()) {
                v = value.getAsBoolean();
            } else if (value.isString()) {
                v = value.getAsString();
            } else {
                throw new IllegalStateException(value.toString());
            }
            result.put(entry.getKey(), v);
        }
        return result;
    }

    public Value value(Engine engine, String value) throws IOException {
        Value result;

        result = valueOpt(engine, value);
        if (result == null) {
            throw new ArgumentException("unknown value: " + value);
        }
        return result;
    }

    public Value valueOpt(Engine engine, String value) throws IOException {
        return values(engine).get(value);
    }

    //-- important values

    public Expire getValueExpire(Engine engine) throws IOException {
        return Expire.fromHuman(value(engine, Type.VALUE_EXPIRE).get());
    }

    public List<String> getValueNotify(Engine engine) throws IOException {
        return Separator.COMMA.split(value(engine, Type.VALUE_CONTACT).get());
    }

    public String getValueImage(Engine engine) throws IOException {
        return value(engine, Type.VALUE_IMAGE).get();
    }

    //--

    public FileNode getLogs() {
        return server.getStageLogs(name);
    }

    public Registry createRegistry(World world, Engine engine) throws IOException {
        return createRegistry(world, getValueImage(engine));
    }

    public Registry createRegistry(World world, String image) throws IOException {
        int idx;
        String host;
        Settings.UsernamePassword up;
        String uri;

        idx = image.indexOf('/');
        if (idx == -1) {
            throw new IllegalArgumentException(image);
        }
        host = image.substring(0, idx);
        uri = "https://";
        up = server.settings.registryCredentials(host);
        if (up != null) {
            uri = uri + up.username + ":" + up.password + "@";
        }
        uri = uri + host;
        return PortusRegistry.create(world, uri, null);
    }


    //-- fields

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

        result = valueOpt(engine, str);
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
        for (Value p : values(engine).values()) {
            lst.add(p.name());
        }
        throw new ArgumentException(str + ": no such status field or value, choose one of " + lst);
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
        fields.add(new Field("available") {
            @Override
            public Object get(Context context) throws IOException {
                return context.engine.deploymentProbe(Type.deploymentName(name)).statusAvailable;
            }
        });
        fields.add(new Field("last-deployed") {
            @Override
            public Object get(Context context) throws IOException {
                return helmObject(context.engine).get("info").getAsJsonObject().get("last_deployed").getAsString();
            }
        });
        fields.add(new Field("first-deployed") {
            @Override
            public Object get(Context context) throws IOException {
                return helmObject(context.engine).get("info").getAsJsonObject().get("first_deployed").getAsString();
            }
        });
        fields.add(new Field("cpu") {
            @Override
            public Object get(Context context) throws IOException {
                Stats stats;

                stats = statsOpt(context);
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
                Stats stats;

                stats = statsOpt(context);
                if (stats != null) {
                    return stats.memory;
                } else {
                    return "n.a.";
                }
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

    private Stats statsOpt(Context context) throws IOException {
        Collection<PodInfo> running;

        running = context.runningPods(this).values();
        if (running.isEmpty()) {
            return null;
        }
        return OpenShift.create().statsOpt(running.iterator().next() /* TODO */.name, Type.MAIN_CONTAINER);
    }

    /** @return logins */
    public Set<String> notifyLogins(Engine engine) throws IOException {
        Set<String> done;
        String login;

        done = new HashSet<>();
        for (String user : getValueNotify(engine)) {
            switch (user) {
                case NOTIFY_LAST_MODIFIER:
                    login = lastModifiedBy();
                    break;
                case NOTIFY_FIRST_MODIFIER:
                    login = firstModifiedBy();
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
        String path;

        path = Registry.getRepositoryPath(Registry.toRepository(getValueImage(engine)));
        return registry.list(path);
    }

    /** @param imageOrRepositoryOpt null to keep current image */
    public String publish(Engine engine, String imageOrRepositoryOpt, Map<String, String> clientValues) throws IOException {
        Map<String, Object> map;
        String imageOrRepository;

        map = new HashMap<>(helmValues(engine));
        imageOrRepository = imageOrRepositoryOpt == null ? (String) map.get("image") : imageOrRepositoryOpt;
        return install(true, map, imageOrRepository, clientValues);
    }

    /**
     * @return imageOrRepository exact image or repository to publish latest image from
     */
    private String install(boolean upgrade, Map<String, Object> map, String imageOrRepository, Map<String, String> clientValues) throws IOException {
        Expressions expressions;
        Application app;
        World world;
        FileNode tmp;
        TagInfo image;
        FileNode values;
        FileNode src;
        Expire expire;
        Registry registry;

        validateRepository(Registry.toRepository(imageOrRepository));
        world = World.create(); // TODO
        registry = createRegistry(world, imageOrRepository);
        image = registry.resolve(imageOrRepository);
        expressions = new Expressions(world, server, image, stageFqdn());
        app = Application.load(expressions, world.file("/etc/charts/app.yaml").readString());
        tmp = world.getTemp().createTempDirectory();
        values = world.getTemp().createTempFile();
        src = world.file("/etc/charts").join(app.chart);
        if (!src.isDirectory()) {
            throw new ArgumentException("helm chart not found: " + app.chart);
        }
        src.copyDirectory(tmp);
        Type.TYPE.checkValues(clientValues, builtInValues(tmp).keySet());
        app.addValues(expressions, map);
        map.putAll(clientValues);
        expire = Expire.fromHuman((String) map.getOrDefault(Type.VALUE_EXPIRE, Integer.toString(server.settings.defaultExpire)));
        if (expire.isExpired()) {
            throw new ArgumentException(name + ": stage expired: " + expire);
        }
        map.put(Type.VALUE_EXPIRE, expire.toString()); // normalize
        Server.LOGGER.info("values: " + map);
        try (PrintWriter v = new PrintWriter(values.newWriter())) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                v.println(entry.getKey() + ": " + toJson(entry.getValue()));
            }
        }
        try {
            Server.LOGGER.info("helm install upgrade=" + upgrade);
            Server.LOGGER.info(tmp.exec("helm", upgrade ? "upgrade" : "install", "--debug", "--values", values.getAbsolute(), name, tmp.getAbsolute()));
        } finally {
            tmp.deleteTree();
        }
        lazyHelmObject = null; // force reload
        return image.repositoryTag;
    }

    public void awaitAvailable(Engine engine) throws IOException {
        engine.deploymentAwaitAvailable(Type.deploymentName(name));
    }

    public Map<String, String> builtInValues(FileNode chart) throws IOException {
        ObjectMapper yaml;
        ObjectNode root;
        Map<String, String> result;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;

        yaml = new ObjectMapper(new YAMLFactory());
        try (Reader src = chart.join("values.yaml").newReader()) {
            root = (ObjectNode) yaml.readTree(src);
        }
        result = new HashMap<>();
        iter = root.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            result.put(entry.getKey(), entry.getValue().asText());
        }
        return result;
    }

    // this is to avoid engine 500 error reporting "invalid reference format: repository name must be lowercase"
    public static void validateRepository(String repository) {
        URI uri;

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

    public void uninstall(Engine engine) throws IOException {
        Server.LOGGER.info(World.createMinimal().getWorking().exec("helm", "uninstall", getName()));
        engine.deploymentAwaitGone(getName());
    }

    //--

    /** @return login name or null if unknown */
    public String firstModifiedBy() throws IOException {
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
        TagInfo tag;

        result = new LinkedHashMap<>();
        tag = registry.tagInfo(getValueImage(engine));
        addNamed("http", url(tag, "http"), result);
        addNamed("https", url(tag, "https"), result);
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
        return name + "." + server.settings.fqdn;
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

    //--

    public Map<String, PodInfo> runningPods(Engine engine) throws IOException {
        return engine.podList(engine.deploymentProbe(Type.deploymentName(name)).selector);
    }

    /** @return never null */
    public TagInfo tagInfo(Engine engine, Registry registry) throws IOException {
        return registry.tagInfo(getValueImage(engine));
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
}
