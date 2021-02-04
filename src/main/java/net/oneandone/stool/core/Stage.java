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
package net.oneandone.stool.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.cli.Caller;
import net.oneandone.stool.helmclasses.ClassRef;
import net.oneandone.stool.helmclasses.Clazz;
import net.oneandone.stool.helmclasses.Helm;
import net.oneandone.stool.kubernetes.Stats;
import net.oneandone.stool.util.Expire;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.stool.util.Json;
import net.oneandone.stool.util.Diff;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A short-lived object, created for one request, discarded afterwards - caches results for performance.
 * CAUTION: has to be reloaed to reflect e.g. value changes.
 */
public class Stage {
    private static final Logger LOGGER = LoggerFactory.getLogger(Stage.class);

    public static Stage create(Caller caller, Engine engine, Configuration configuration, String stageName, ClassRef classRef,
                               Map<String, String> values) throws IOException {
        List<HistoryEntry> history;
        Stage stage;

        history = new ArrayList<>(1);
        history.add(HistoryEntry.create(caller));
        Helm.install(configuration, stageName, classRef, values);
        stage = Stage.create(configuration, stageName, engine.helmRead(stageName), history);
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


    public static Stage create(Configuration configuration, String name, ObjectNode helmObject, List<HistoryEntry> history) throws IOException {
        Map<String, Clazz> all;
        Clazz cl;

        // TODO: expensive
        cl = Clazz.loadHelm((ObjectNode) ((ObjectNode) helmObject.get("config")).remove(Clazz.HELM_CLASS));
        return new Stage(configuration, name, cl, values(cl, helmObject), (ObjectNode) helmObject.get("info"), history);
    }

    private static final List<String> WITHOUT_CLASS = Collections.singletonList("class");

    private static Map<String, Value> values(Clazz clazz, ObjectNode helmObject) throws IOException {
        Map<String, Object> raw;
        Map<String, Value> result;
        String key;

        raw = Json.toStringMap((ObjectNode) helmObject.get("chart").get("values"), WITHOUT_CLASS);
        raw.putAll(Json.toStringMap((ObjectNode) helmObject.get("config"), Collections.EMPTY_LIST));
        check(raw, Dependencies.MANDATORY);
        result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            key = entry.getKey();
            result.put(key, new Value(clazz.get(key), entry.getValue().toString()));
        }
        return result;
    }

    private static void check(Map<String, Object> values, String... keys) throws IOException {
        for (String key : keys) {
            if (!values.containsKey(key)) {
                throw new IOException("missing key in helm chart: " + key);
            }
        }
    }

    //--

    public static final String NOTIFY_FIRST_MODIFIER = "@first";
    public static final String NOTIFY_LAST_MODIFIER = "@last";

    //--

    public final Configuration configuration;

    /**
     * Has a very strict syntax, it's used:
     * * in Kubernetes resource names
     * * Docker repository tags
     * * label values
     */
    private final String name;

    public final Clazz clazz;

    private final Map<String, Value> values;

    private final ObjectNode info;

    public final List<HistoryEntry> history;

    public Stage(Configuration configuration, String name, Clazz clazz, Map<String, Value> values, ObjectNode info, List<HistoryEntry> history) {
        this.configuration = configuration;
        this.name = name;
        this.clazz = clazz;
        this.values = values;
        this.info = info;
        this.history = history;
    }

    public String getName() {
        return name;
    }

    //-- values

    public List<Value> values() {
        return new ArrayList<>(values.values());
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
        return values.get(value);
    }

    public String valueOptString(String key, String dflt) {
        Value result;

        result = valueOpt(key);
        return result == null ? dflt : result.get();
    }

    //-- important values

    public Expire getMetadataExpire() {
        return Expire.fromHuman(values.get(Dependencies.VALUE_EXPIRE).get());
    }

    public List<String> getMetadataNotify() {
        return Separator.COMMA.split(values.get(Dependencies.VALUE_CONTACT).get());
    }

    //--

    public FileNode getLogs() throws MkdirException {
        return configuration.stageLogs(name);
    }

    //-- properties

    public Property propertyOpt(String str) {
        for (Property f : properties()) {
            if (str.equals(f.name())) {
                return f;
            }
        }
        return null;
    }

    public List<Property> properties() {
        List<Property> properties;

        properties = new ArrayList<>();
        properties.add(new Property("name") {
            @Override
            public Object get(Engine engine) {
                return name;
            }
        });
        properties.add(new Property("available") {
            @Override
            public Object get(Engine engine) throws IOException {
                return engine.deploymentProbe(Dependencies.deploymentName(name)).statusAvailable;
            }
        });
        properties.add(new Property("last-deployed") {
            @Override
            public Object get(Engine engine) {
                return info.get("last_deployed").asText();
            }
        });
        properties.add(new Property("first-deployed") {
            @Override
            public Object get(Engine engine) {
                return info.get("first_deployed").asText();
            }
        });
        properties.add(new Property("cpu") {
            @Override
            public Object get(Engine engine) throws IOException {
                Stats stats;

                stats = statsOpt(engine);
                if (stats != null) {
                    return stats.cpu;
                } else {
                    return "n.a.";
                }
            }
        });
        properties.add(new Property("mem") {
            @Override
            public Object get(Engine engine) throws IOException {
                Stats stats;

                stats = statsOpt(engine);
                if (stats != null) {
                    return stats.memory;
                } else {
                    return "n.a.";
                }
            }
        });
        properties.add(new Property("urls") {
            @Override
            public Object get(Engine engine) {
                return Stage.this.urlMap();
            }
        });
        properties.add(new Property("chart") {
            @Override
            public Object get(Engine engine) {
                return Stage.this.clazz.chart + ":" + Stage.this.clazz.chartVersion;
            }
        });
        properties.add(new Property("class", true) {
            @Override
            public Object get(Engine engine) {
                return Stage.this.clazz.toObject(configuration.yaml).toPrettyString();
            }
        });
        properties.add(new Property("origin", true) {
            @Override
            public Object get(Engine engine) {
                return Stage.this.clazz.origin;
            }
        });
        return properties;
    }

    private Stats statsOpt(Engine engine) throws IOException {
        Collection<PodInfo> running;

        running = runningPods(engine).values();
        if (running.isEmpty()) {
            return null;
        }
        return engine.statsOpt(running.iterator().next() /* TODO */.name, Dependencies.MAIN_CONTAINER);
    }

    /** @return logins */
    public Set<String> notifyLogins() {
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

    /** CAUTION: values are not updated! */
    public Diff publish(Caller caller, Engine engine, boolean dryrun, String allow,
                        Clazz withClazz, Map<String, String> clientValues) throws IOException {
        Diff diff;

        diff = Helm.upgrade(configuration, name, dryrun, allow == null ? null : Separator.COMMA.split(allow), withClazz, clientValues, valuesMap());
        history.add(HistoryEntry.create(caller));
        saveHistory(engine);
        // TODO: update values in this stage instance? or return new instance?
        return diff;
    }

    private Map<String, String> valuesMap() {
        Map<String, String> result;

        result = new LinkedHashMap<>();
        for (Map.Entry<String, Value> entry : values.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return result;
    }

    /** CAUTION: values are not updated! */
    public void setValues(Caller caller, Engine engine, Map<String, String> changes) throws IOException {
        Map<String, String> map;

        map = new LinkedHashMap<>();
        for (Map.Entry<String, Value> entry : values.entrySet()) {
            map.put(entry.getKey(), entry.getValue().get());
        }
        map.putAll(changes);
        Helm.upgrade(configuration, name, false, null, clazz, map, valuesMap());
        history.add(HistoryEntry.create(caller));
        saveHistory(engine);
        // TODO: update values in this stage instance? or return new instance?
    }

    private void saveHistory(Engine engine) throws IOException {
        engine.secretAddAnnotations(engine.helmSecretName(name), historyToMap(history));
    }

    public void uninstall(Engine engine) throws IOException {
        LOGGER.info(configuration.world.getWorking().exec("helm", "uninstall", getName()));
        engine.deploymentAwaitGone(getName());
    }

    public void awaitAvailable(Engine engine) throws IOException {
        engine.deploymentAwaitAvailable(Dependencies.deploymentName(name));
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
    public Map<String, String> urlMap() {
        Map<String, String> result;

        result = new LinkedHashMap<>();
        addNamed("http", url("http"), result);
        addNamed("https", url("https"), result);
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

    //--

    private List<String> url(String protocol) {
        String fqdn;
        String url;
        List<String> result;

        fqdn = configuration.stageFqdn(name);
        url = protocol + "://" + fqdn + "/" + urlContext();
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        result = new ArrayList<>();
        for (String suffix : urlSuffixes()) {
            result.add(url + suffix);
        }
        return result;
    }

    private static final Separator SUFFIXES_SEP = Separator.on(',').trim();

    private List<String> urlSuffixes() {
        String suffixes;
        List<String> result;

        suffixes = valueOptString("urlSuffixes", "");
        result = new ArrayList<>();
        if (suffixes != null) {
            result.addAll(SUFFIXES_SEP.split(suffixes));
        }
        if (result.isEmpty()) {
            result.add("");
        }
        return result;
    }

    private String urlContext() {
        String result;

        result = valueOptString("urlContext", "");
        if (result.startsWith("/")) {
            throw new ArithmeticException("server must not start with '/': " + result);
        }
        if (!result.isEmpty() && result.endsWith("/")) {
            throw new ArithmeticException("server must not end with '/': " + result);
        }
        return result;
    }

    //--

    public Map<String, PodInfo> runningPods(Engine engine) throws IOException {
        return engine.podList(engine.deploymentProbe(Dependencies.deploymentName(name)).selector);
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
