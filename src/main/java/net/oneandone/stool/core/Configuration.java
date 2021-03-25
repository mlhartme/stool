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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.directions.Direction;
import net.oneandone.stool.directions.Directions;
import net.oneandone.stool.directions.Freemarker;
import net.oneandone.stool.directions.Toolkit;
import net.oneandone.stool.directions.Variable;
import net.oneandone.stool.util.Diff;
import net.oneandone.stool.util.Expire;
import net.oneandone.stool.util.Json;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The directions needed to configure a stage. To make it independent from toolkit (changes).
 * TODO: rename to Config?
 */
public class Configuration {
    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    private static final String DIRECTIONS_VALUE = "_directions";

    public static Configuration create(Toolkit toolkit, Directions directions, Map<String, String> values) throws IOException {
        Configuration result;

        result = new Configuration();
        result.layers.add(Directions.configDirections(values));
        result.addDirections(toolkit, directions);
        return result;
    }

    public static Configuration loadAndEat(ObjectNode helmConfig) throws IOException {
        Configuration result;

        result = new Configuration();
        for (JsonNode entry : helmConfig.remove(DIRECTIONS_VALUE)) {
            result.layers.add(Directions.load((ObjectNode) entry));
        }
        return result;
    }

    //--

    // config is first; directions without base
    private final List<Directions> layers;

    private Configuration() {
        layers = new ArrayList<>();
    }

    private void addDirections(Toolkit toolkit, Directions directions) throws IOException {
        Directions chartLayer;

        layers.addAll(directions.createLayers(toolkit));
        chartLayer = chartLayer();
        if (chartLayer != null) { // TODO: for tests
            layers.add(toolkit.chart(chartLayer.chartOpt).directions.clone());
        }
    }

    public Configuration withDirections(Toolkit toolkit, Directions directions) throws IOException {
        Configuration result;

        result = new Configuration();
        result.layers.add(config().clone());
        result.addDirections(toolkit, directions);
        return result;
    }

    public Configuration withConfig(Map<String, String> overrides) {
        Configuration result;

        result = clone();
        result.config().setValues(overrides);
        return result;
    }

    public Configuration clone() {
        Configuration result;

        result = new Configuration();
        for (Directions layer : layers) {
            result.layers.add(layer.clone());
        }
        return result;
    }


    public Set<String> names() {
        Set<String> result;

        result = new HashSet<>();
        for (Directions layer : layers) {
            result.addAll(layer.directions.keySet());
        }
        return result;
    }

    public Map<String, Direction> execDirections() {
        Map<String, Direction> result;

        result = new HashMap<>();
        for (String name: names()) {
            result.put(name, exprLayer(name).get(name));
        }
        return result;
    }


    private Directions config() {
        return layers.get(0);
    }

    private Directions chartLayer() {
        for (Directions d : layers) {
            if (d.chartOpt != null) {
                return d;
            }
        }
        return null;
    }

    private Directions exprLayer(String name) {
        Direction one;
        Directions empty;

        empty = null;
        for (Directions layer : layers) {
            one = layer.directions.get(name);
            if (one != null && one.expression != null) {
                if (!one.expression.isEmpty()) {
                    return layer;
                } else {
                    if (empty == null) {
                        empty = layer;
                    }
                }
            }
        }
        return empty;
    }

    public boolean priv(String name) {
        Direction one;

        for (Directions layer : layers) {
            one = layer.directions.get(name);
            if (one != null && one.priv) {
                return true;
            }
        }
        return false;
    }

    public String doc(String name) {
        Direction one;

        for (Directions layer : layers) {
            one = layer.directions.get(name);
            if (one != null && one.doc != null) {
                return one.doc;
            }
        }
        return "";
    }

    public String chartString() {
        Directions d;

        d = chartLayer();
        return d.chartOpt + ":" + d.chartVersionOpt;
    }

    public String value(Variable variable) {
        String result;
        Directions d;
        Direction one;

        result = variable.get();
        d = exprLayer(variable.name);
        if (d == null) {
            // TODO replicas in stool chart ...
        } else {
            result = result + " [" + d.subject + "]";
            one = d.get(variable.name);
            if (!one.isValue()) {
                result = result + " " + one.expression;
            }
        }
        return result;
    }

    public ArrayNode toArray(ObjectMapper yaml) {
        ArrayNode result;

        result = yaml.createArrayNode();
        for (Directions layer : layers) {
            result.add(layer.toObject(yaml));
        }
        return result;
    }


    public String origin() {
        return "TODO";
    }


    public FileNode createValuesFile(ObjectMapper yaml, World world, Map<String, String> actuals) throws IOException {
        ObjectNode dest;
        Expire expire;
        FileNode file;
        String str;

        dest = yaml.createObjectNode();
        for (Map.Entry<String, String> entry : actuals.entrySet()) {
            dest.put(entry.getKey(), entry.getValue());
        }

        dest.set(DIRECTIONS_VALUE, toArray(yaml));

        // check expire - TODO: ugly up reference to core package
        str = Json.string(dest, Dependencies.VALUE_EXPIRE, null);
        if (str != null) {
            expire = Expire.fromString(str);
            if (expire.isExpired()) {
                throw new ArgumentException("stage expired: " + expire);
            }
            dest.put(Dependencies.VALUE_EXPIRE, expire.toString());
        }

        file = world.getTemp().createTempFile().writeString(dest.toPrettyString());
        return file;
    }

    public Map<String, Variable> loadVariables(ObjectNode helmObject) {
        Map<String, Object> raw;
        Map<String, Variable> result;
        String key;

        raw = Json.toStringMap((ObjectNode) helmObject.get("chart").get("values"), Collections.emptyList());
        raw.putAll(Json.toStringMap((ObjectNode) helmObject.get("config"), Collections.emptyList()));
        result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            key = entry.getKey();
            result.put(key, new Variable(key, priv(key), doc(key), entry.getValue().toString()));
        }
        return result;
    }

    //--

    public Diff helm(String kubeContext, LocalSettings localSettings, String name, boolean upgrade, boolean dryrun, List<String> allowOpt,
                            Map<String, String> prev) throws IOException {
        Toolkit toolkit;
        Freemarker freemarker;
        FileNode valuesFile;
        Map<String, String> values;
        Diff result;
        Diff forbidden;

        toolkit = localSettings.toolkit();
        LOGGER.info("chart: " + chartString());
        freemarker = toolkit.freemarker(localSettings.getLib(), name, localSettings.fqdn);
        values = freemarker.eval(prev, execDirections().values(), toolkit.scripts);
        result = Diff.diff(prev, values);
        if (allowOpt != null) {
            forbidden = result.withoutKeys(allowOpt);
            if (!forbidden.isEmpty()) {
                throw new IOException("change is forbidden:\n" + forbidden);
            }
        }
        removePrivate(result);
        valuesFile = createValuesFile(localSettings.yaml, localSettings.world, values);
        try {
            LOGGER.info("values: " + valuesFile.readString());
            helm(dryrun, kubeContext,
                    localSettings.home, upgrade ? "upgrade" : "install", "--debug", "--values", valuesFile.getAbsolute(), name,
                    toolkit.chart(chartLayer().chartOpt).reference);
            return result;
        } finally {
            valuesFile.deleteFile();
        }
    }

    private Directions merged() {
        Directions result;
        Directions layer;

        result = null;
        for (int i = layers.size() - 1; i >= 0; i--) {
            layer = layers.get(i);
            if (result == null) {
                result = layer.clone();
            } else {
                layer.addMergedWithoutBases(result);
            }

        }
        return result;
    }

    private void removePrivate(Diff result) {
        for (String name : names()) {
            if (priv(name)) {
                result.remove(name);
            }
        }
    }

    //--

    public static void helm(boolean dryrun, String kubeContext, FileNode dir, String... args) throws IOException {
        String[] cmd;

        if (kubeContext != null) {
            cmd = Strings.cons("--kube-context", Strings.cons(kubeContext, args));
        } else {
            cmd = args;
        }
        cmd = Strings.cons("helm", cmd);
        LOGGER.debug(Arrays.asList(cmd).toString());
        if (dryrun) {
            LOGGER.info("dryrun - skipped");
        } else {
            LOGGER.info(dir.exec(cmd));
        }
    }

    //--

    // TODO
    public static Directions merged(Toolkit toolkit, Directions directions) throws IOException {
        return Configuration.create(toolkit, directions, Collections.emptyMap()).merged();
    }
}
