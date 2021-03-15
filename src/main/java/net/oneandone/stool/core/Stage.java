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
import net.oneandone.stool.directions.Variable;
import net.oneandone.stool.cli.Caller;
import net.oneandone.stool.directions.DirectionsRef;
import net.oneandone.stool.directions.Directions;
import net.oneandone.stool.directions.Helm;
import net.oneandone.stool.kubernetes.Stats;
import net.oneandone.stool.util.Expire;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.stool.util.Json;
import net.oneandone.stool.util.Diff;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A short-lived object, created for one request, discarded afterwards - caches results for performance.
 * CAUTION: has to be reloaed to reflect e.g. value changes.
 */
public class Stage {
    public static Stage create(Caller caller, String kubeContext, Engine engine, LocalSettings localSettings, String stageName,
                               DirectionsRef directionsRef, Map<String, String> values) throws IOException {
        List<HistoryEntry> history;
        Stage stage;

        history = new ArrayList<>(1);
        history.add(HistoryEntry.create(caller));
        Helm.install(kubeContext, localSettings, stageName, directionsRef, values);
        stage = Stage.create(localSettings, stageName, engine.helmRead(stageName), history);
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


    public static Stage create(LocalSettings localSettings, String name, ObjectNode helmObject, List<HistoryEntry> history) throws IOException {
        Directions cl;

        cl = Directions.loadHelm((ObjectNode) ((ObjectNode) helmObject.get("config")).remove(Directions.DIRECTIONS_VALUE));
        return new Stage(localSettings, name, cl, loadVariables(cl, helmObject), (ObjectNode) helmObject.get("info"), history);
    }

    private static Map<String, Variable> loadVariables(Directions directions, ObjectNode helmObject) throws IOException {
        Map<String, Object> raw;
        Map<String, Variable> result;
        String key;

        raw = Json.toStringMap((ObjectNode) helmObject.get("chart").get("values"), Collections.emptyList());
        raw.putAll(Json.toStringMap((ObjectNode) helmObject.get("config"), Collections.EMPTY_LIST));
        result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            key = entry.getKey();
            result.put(key, new Variable(directions.get(key), entry.getValue().toString()));
        }
        return result;
    }

    //--

    private final LocalSettings localSettings;

    /**
     * Has a very strict syntax, it's used:
     * * in Kubernetes resource names
     * * Docker repository tags
     * * label values
     */
    private final String name;

    public final Directions directions;

    private final Map<String, Variable> variables;

    private final ObjectNode info;

    public final List<HistoryEntry> history;

    public Stage(LocalSettings localSettings, String name, Directions directions, Map<String, Variable> variables, ObjectNode info, List<HistoryEntry> history) {
        this.localSettings = localSettings;
        this.name = name;
        this.directions = directions;
        this.variables = variables;
        this.info = info;
        this.history = history;
    }

    public String getName() {
        return name;
    }

    //-- values

    public List<Variable> variables() {
        return new ArrayList<>(variables.values());
    }

    public Variable variable(String key) {
        Variable result;

        result = variableOpt(key);
        if (result == null) {
            throw new ArgumentException("unknown value: " + key);
        }
        return result;
    }

    public Variable variableOpt(String value) {
        return variables.get(value);
    }

    public String variableOptString(String key, String dflt) {
        Variable result;

        result = variableOpt(key);
        return result == null ? dflt : result.get();
    }

    //-- important values

    public Expire getMetadataExpireOpt() {
        Variable variable;

        variable = variableOpt(Dependencies.VALUE_EXPIRE);
        return variable == null ? null : Expire.fromString(variable.get());
    }

    public List<String> getMetadataContact() {
        return Separator.COMMA.split(variableOptString(Dependencies.VALUE_CONTACT, ""));
    }

    //--

    public FileNode getLogs() throws MkdirException {
        return localSettings.stageLogs(name);
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

    public List<Field> fields() {
        List<Field> fields;

        fields = new ArrayList<>();
        fields.add(new Field("name") {
            @Override
            public Object get(Engine engine) {
                return name;
            }
        });
        fields.add(new Field("available") {
            @Override
            public Object get(Engine engine) throws IOException {
                return engine.deploymentProbe(Dependencies.deploymentName(name)).statusAvailable;
            }
        });
        fields.add(new Field("last-deployed") {
            @Override
            public Object get(Engine engine) {
                return info.get("last_deployed").asText();
            }
        });
        fields.add(new Field("first-deployed") {
            @Override
            public Object get(Engine engine) {
                return info.get("first_deployed").asText();
            }
        });
        fields.add(new Field("cpu") {
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
        fields.add(new Field("mem") {
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
        fields.add(new Field("urls") {
            @Override
            public Object get(Engine engine) {
                return Stage.this.urlMap();
            }
        });
        fields.add(new Field("chart") {
            @Override
            public Object get(Engine engine) {
                return Stage.this.directions.chartOpt + ":" + Stage.this.directions.chartVersionOpt;
            }
        });
        fields.add(new Field("directions", true) {
            @Override
            public Object get(Engine engine) {
                return Stage.this.directions.toObject(localSettings.yaml).toPrettyString();
            }
        });
        fields.add(new Field("origin", true) {
            @Override
            public Object get(Engine engine) {
                return Stage.this.directions.origin;
            }
        });
        return fields;
    }

    private Stats statsOpt(Engine engine) throws IOException {
        Collection<PodInfo> running;

        running = runningPods(engine).values();
        if (running.isEmpty()) {
            return null;
        }
        return engine.statsOpt(running.iterator().next() /* TODO */.name, Dependencies.MAIN_CONTAINER);
    }

    /** CAUTION: values are not updated! */
    public Diff publish(Caller caller, String kubeContext, Engine engine, boolean dryrun, String allow,
                        Directions withClass, Map<String, String> overrides) throws IOException {
        Diff diff;

        diff = Helm.upgrade(kubeContext, localSettings, name, dryrun, allow == null ? null : Separator.COMMA.split(allow),
                withClass, overrides, valuesMap());
        history.add(HistoryEntry.create(caller));
        saveHistory(engine);
        // TODO: update values in this stage instance? or return new instance?
        return diff;
    }

    private Map<String, String> valuesMap() {
        Map<String, String> result;

        result = new LinkedHashMap<>();
        for (Map.Entry<String, Variable> entry : variables.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return result;
    }

    public void setValues(Caller caller, String kubeContext, Engine engine, Map<String, String> changes) throws IOException {
        Map<String, String> prev;
        Map<String, String> overrides;

        prev = valuesMap();
        overrides = new LinkedHashMap<>(prev); // override all, do not compute any values
        overrides.putAll(changes);
        Helm.upgrade(kubeContext, localSettings, name, false, null, directions, overrides, prev);
        history.add(HistoryEntry.create(caller));
        saveHistory(engine);
        // TODO: update values in this stage instance? or return new instance?
    }

    private void saveHistory(Engine engine) throws IOException {
        engine.secretAddAnnotations(engine.helmSecretName(name), historyToMap(history));
    }

    public void uninstall(String kubeContext, Engine engine) throws IOException {
        Helm.exec(false, kubeContext, localSettings.world.getWorking(), "uninstall", getName());
        engine.deploymentAwaitGone(getName());
    }

    public void awaitAvailable(Engine engine) throws IOException {
        engine.deploymentAwaitAvailable(Dependencies.deploymentName(name));
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
        String url;
        List<String> result;
        String prefix;

        result = new ArrayList<>();
        for (String subdomain : urlSubdomains()) {
            prefix = subdomain.trim();
            if (!prefix.isEmpty()) {
                prefix = prefix + ".";
            }
            url = protocol + "://" + prefix + name + "." + localSettings.fqdn + "/" + urlContext();
            if (!url.endsWith("/")) {
                url = url + "/";
            }
            for (String suffix : urlSuffixes()) {
                result.add(url + suffix);
            }
        }
        return result;
    }

    private static final Separator SUFFIXES_SEP = Separator.on(',').trim();

    private List<String> urlSuffixes() {
        String suffixes;
        List<String> result;

        suffixes = variableOptString(Dependencies.URL_SUFFIXES, "");
        result = new ArrayList<>();
        if (suffixes != null) {
            result.addAll(SUFFIXES_SEP.split(suffixes));
        }
        if (result.isEmpty()) {
            result.add("");
        }
        return result;
    }

    private List<String> urlSubdomains() {
        String str;
        List<String> result;

        str = variableOptString(Dependencies.URL_SUBDOMAINS, "");
        result = new ArrayList<>();
        if (str != null) {
            result.addAll(SUFFIXES_SEP.split(str));
        }
        if (result.isEmpty()) {
            result.add("");
        }
        return result;
    }

    private String urlContext() {
        String result;

        result = variableOptString(Dependencies.URL_CONTEXT, "");
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

    /* @return null if unkown (e.g. because log file was wiped) */
    public HistoryEntry oldest() {
        return history.isEmpty() ? null : history.get(0);
    }
}
