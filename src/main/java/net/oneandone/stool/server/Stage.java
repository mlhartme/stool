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
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.client.Caller;
import net.oneandone.stool.kubernetes.Stats;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.registry.TagInfo;
import net.oneandone.stool.server.settings.Expire;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.stool.server.util.Context;
import net.oneandone.stool.server.util.Field;
import net.oneandone.stool.server.util.Info;
import net.oneandone.stool.server.util.Value;
import net.oneandone.stool.server.values.Helm;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(Stage.class);

    public static Stage create(Caller caller, Engine engine, Server server, String name, String image, Map<String, String> values) throws IOException {
        List<HistoryEntry> history;
        Stage stage;

        history = new ArrayList<>(1);
        history.add(HistoryEntry.create(caller));
        Helm.run(World.create().file(server.configuration.charts), server, name, false, new HashMap<>(), image, values);
        stage = Stage.create(server, name, engine.helmRead(name), history);
        stage.saveHistory(engine);
        return stage;
    }

    private static final String HISTORY_PREFIX = "stool-";

    public static Map<String, String> historyToMap(List<HistoryEntry> history) {
        Map<String, String> map;

        map = new HashMap<>(history.size());
        for (int i = 0; i < history.size(); i++) {
            map.put(HISTORY_PREFIX + i, history.get(i).toString());
        }
        return map;
    }
    public static List<HistoryEntry> historyFromMap(Map<String, String> annotations) {
        List<HistoryEntry> result;
        String value;

        result = new ArrayList<>();
        if (annotations != null) {
            for (int n = 0; true; n++) {
                value = annotations.get(HISTORY_PREFIX + n);
                if (value == null) {
                    break;
                }
                result.add(HistoryEntry.parse(value));
            }
        }
        return result;
    }


    public static Stage create(Server server, String name, JsonObject helmObject, List<HistoryEntry> history) throws IOException {
        return new Stage(server, name, values(helmObject), helmObject.get("info").getAsJsonObject(), history);
    }

    private static Map<String, Object> values(JsonObject helmObject) throws IOException {
        Map<String, Object> result;

        result = toStringMap(helmObject.get("chart").getAsJsonObject().get("values").getAsJsonObject());
        result.putAll(toStringMap(helmObject.get("config").getAsJsonObject()));
        check(result, Type.MANDATORY);
        return result;
    }

    private static void check(Map<String, Object> values, String... keys) throws IOException {
        for (String key : keys) {
            if (!values.containsKey(key)) {
                throw new IOException("missing key in helm chart: " + key);
            }
        }
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

    private final Map<String, Object> values;

    private final JsonObject info;

    public final List<HistoryEntry> history;

    public Stage(Server server, String name, Map<String, Object> values, JsonObject info, List<HistoryEntry> history) {
        this.server = server;
        this.name = name;
        this.values = values;
        this.info = info;
        this.history = history;
    }

    public String getName() {
        return name;
    }

    //-- values

    public List<Value> values() {
        List<Value> result;

        result = new ArrayList<>();
        for (String key : values.keySet()) {
            result.add(value(key));
        }
        return result;
    }
    public Value value(String key) {
        Value result;

        result = valueOpt(key);
        if (result == null) {
            throw new ArgumentException("unknown value: " + key);
        }
        return result;
    }
    public Value valueOpt(String value) {
        Object obj;

        obj = values.get(value);
        return obj == null ? null : new Value(value, obj.toString());
    }

    //-- important values

    public Expire getMetadataExpire() {
        return Expire.fromHuman(values.get(Type.VALUE_EXPIRE).toString());
    }

    public List<String> getMetadataNotify() {
        return Separator.COMMA.split(values.get(Type.VALUE_CONTACT).toString());
    }

    public String getImage() {
        return values.get(Type.VALUE_IMAGE).toString();
    }

    //--

    public FileNode getLogs() {
        return server.getStageLogs(name);
    }

    public Registry createRegistry(World world) throws IOException {
        return server.createRegistry(world, getImage());
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
        for (Value v : values()) {
            lst.add(v.name());
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
        return context.engine.statsOpt(running.iterator().next() /* TODO */.name, Type.MAIN_CONTAINER);
    }

    /** @return logins */
    public Set<String> notifyLogins() throws IOException {
        Set<String> done;
        String login;

        done = new HashSet<>();
        for (String user : getMetadataNotify()) {
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

        path = Registry.getRepositoryPath(Registry.toRepository(getImage()));
        return registry.list(path);
    }

    /** CAUTION: values are not updated!
     * @param imageOrRepositoryOpt null to keep current image
     */
    public String publish(Caller caller, Engine engine, String imageOrRepositoryOpt, Map<String, String> clientValues) throws IOException {
        Map<String, Object> map;
        String imageOrRepository;
        String result;

        map = new HashMap<>(values);
        imageOrRepository = imageOrRepositoryOpt == null ? (String) map.get("image") : imageOrRepositoryOpt;
        result = Helm.run(World.create().file(server.configuration.charts) /* TODO */,
                server, name, true, map, imageOrRepository, clientValues);
        history.add(HistoryEntry.create(caller));
        saveHistory(engine);
        return result;
    }

    private void saveHistory(Engine engine) throws IOException {
        engine.secretAddAnnotations(engine.helmSecretName(name), historyToMap(history));
    }

    public void uninstall(Engine engine) throws IOException {
        LOGGER.info(World.createMinimal().getWorking().exec("helm", "uninstall", getName()));
        engine.deploymentAwaitGone(getName());
    }

    public void awaitAvailable(Engine engine) throws IOException {
        engine.deploymentAwaitAvailable(Type.deploymentName(name));
    }

    //--

    /** @return login name or null if unknown */
    public String firstModifiedBy() {
        HistoryEntry entry;

        entry = oldest();
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
        tag = registry.tagInfo(getImage());
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
        return registry.tagInfo(getImage());
    }

    /** @return null if unknown */
    public String lastModifiedBy() {
        HistoryEntry entry;

        entry = youngest();
        return entry == null ? null : entry.user;
    }

    /* @return null if unknown (e.g. because log file was wiped) */
    public HistoryEntry youngest() {
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }


    /* @return null if unkown (e.g. because log file was wiped) */
    public HistoryEntry oldest() {
        return history.isEmpty() ? null : history.get(0);
    }
}
