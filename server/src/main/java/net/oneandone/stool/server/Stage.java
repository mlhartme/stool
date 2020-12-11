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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.oneandone.stool.kubernetes.OpenShift;
import net.oneandone.stool.kubernetes.Stats;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.registry.TagInfo;
import net.oneandone.stool.server.settings.Expire;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.stool.server.logging.AccessLogEntry;
import net.oneandone.stool.server.util.Context;
import net.oneandone.stool.server.util.Field;
import net.oneandone.stool.server.util.Info;
import net.oneandone.stool.server.util.Value;
import net.oneandone.stool.server.values.Helm;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A short-lived object, created for one request, discarded afterwards - caches results for performance.
 */
public class Stage {
    public static Stage create(Engine engine, Server server, String name, String image, Map<String, String> values) throws IOException {
        Stage stage;

        Helm.run(server, name, false, new HashMap<>(), image, values);
        stage = Stage.create(server, name, engine.helmRead(name));
        return stage;
    }

    public static Stage create(Server server, String name, JsonObject helmObject) {
        return new Stage(server, name, values(helmValues(helmObject)), helmObject.get("info").getAsJsonObject());
    }

    private static Map<String, Object> helmValues(JsonObject helmObject) {
        Map<String, Object> result;

        result = toStringMap(helmObject.get("chart").getAsJsonObject().get("values").getAsJsonObject());
        result.putAll(toStringMap(helmObject.get("config").getAsJsonObject()));
        return result;
    }

    private static Map<String, Value> values(Map<String, Object> helmValues) {
        Map<String, Value> result;

        result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : helmValues.entrySet()) {
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

    private final Map<String, Value> values;

    private final JsonObject info;

    public Stage(Server server, String name, Map<String, Value> values, JsonObject info) {
        this.server = server;
        this.name = name;
        this.values = values;
        this.info = info;
    }

    public String getName() {
        return name;
    }

    //-- values

    public List<Value> values() {
        return new ArrayList<>(values.values());
    }

    public Value value(String value) {
        Value result;

        result = valueOpt(value);
        if (result == null) {
            throw new ArgumentException("unknown value: " + value);
        }
        return result;
    }

    public Value valueOpt(String value) {
        return values.get(value);
    }

    //-- important values

    public Expire getValueExpire() {
        return Expire.fromHuman(value(Type.VALUE_EXPIRE).get());
    }

    public List<String> getValueNotify() {
        return Separator.COMMA.split(value(Type.VALUE_CONTACT).get());
    }

    public String getValueImage() {
        return value(Type.VALUE_IMAGE).get();
    }

    //--

    public FileNode getLogs() {
        return server.getStageLogs(name);
    }

    public Registry createRegistry(World world) throws IOException {
        return server.createRegistry(world, getValueImage());
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

    public Info info(String str) {
        Info result;
        List<String> lst;

        result = valueOpt(str);
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
        for (Value p : values.values()) {
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
            public Object get(Context context) {
                return info.get("last_deployed").getAsString();
            }
        });
        fields.add(new Field("first-deployed") {
            @Override
            public Object get(Context context) {
                return info.get("first_deployed").getAsString();
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
    public Set<String> notifyLogins() throws IOException {
        Set<String> done;
        String login;

        done = new HashSet<>();
        for (String user : getValueNotify()) {
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
    public List<TagInfo> images(Registry registry) throws IOException {
        String path;

        path = Registry.getRepositoryPath(Registry.toRepository(getValueImage()));
        return registry.list(path);
    }

    /** CAUTION: values are not updated!
     * @param imageOrRepositoryOpt null to keep current image
     */
    public String publish(String imageOrRepositoryOpt, Map<String, String> clientValues) throws IOException {
        Map<String, Object> map;
        String imageOrRepository;
        String result;

        map = new HashMap<>(values);
        imageOrRepository = imageOrRepositoryOpt == null ? (String) map.get("image") : imageOrRepositoryOpt;
        result = Helm.run(server, name, true, map, imageOrRepository, clientValues);
        return result;
    }

    public void uninstall(Engine engine) throws IOException {
        Server.LOGGER.info(World.createMinimal().getWorking().exec("helm", "uninstall", getName()));
        engine.deploymentAwaitGone(getName());
    }

    public void awaitAvailable(Engine engine) throws IOException {
        engine.deploymentAwaitAvailable(Type.deploymentName(name));
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
    public Map<String, String> urlMap(Registry registry) throws IOException {
        Map<String, String> result;
        TagInfo tag;

        result = new LinkedHashMap<>();
        tag = registry.tagInfo(getValueImage());
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

    private List<String> url(TagInfo tag, String protocol) {
        String fqdn;
        String url;
        List<String> result;

        fqdn = server.stageFqdn(name);
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
    public TagInfo tagInfo(Registry registry) throws IOException {
        return registry.tagInfo(getValueImage());
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
