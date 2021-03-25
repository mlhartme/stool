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
import net.oneandone.stool.directions.DirectionsRef;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The directions needed to configure a stage. To make it independent from toolkit (changes).
 * TODO: rename to Config?
 */
public class Sequence {
    private static final Logger LOGGER = LoggerFactory.getLogger(Sequence.class);

    private static final String DIRECTIONS_VALUE = "_directions";

    public static Sequence create(Toolkit toolkit, Directions directions, Map<String, String> values) throws IOException {
        Sequence result;

        result = new Sequence();
        result.layers.add(Directions.configDirections(values));
        result.layers.addAll(directions.createLayers(toolkit));
        return result;
    }

    public static Sequence loadAndEat(ObjectNode helmConfig) throws IOException {
        Sequence result;

        result = new Sequence();
        for (JsonNode entry : helmConfig.remove(DIRECTIONS_VALUE)) {
            result.layers.add(Directions.loadLiteral("TODO", "TODO", (ObjectNode) entry));
        }
        return result;
    }

    //--

    // config is first; directions without base
    private final List<Directions> layers;

    private Sequence() {
        layers = new ArrayList<>();
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

        for (Directions layer : layers) {
            one = layer.directions.get(name);
            if (one != null && one.expression != null) {
                return layer;
            }
        }
        return null;
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


    public Sequence nextSequence(LocalSettings localSettings, DirectionsRef directionsRef) throws IOException {
        Directions directions;
        Sequence result;

        result = new Sequence();
        result.layers.add(config().clone());
        directions = directionsRef.resolve(localSettings);
        result.layers.addAll(directions.createLayers(localSettings.toolkit()));
        return result;
    }

    public Sequence nextSequence(Map<String, String> overrides) {
        Sequence result;

        result = clone();
        result.config().setValues(overrides);
        return result;
    }

    public Sequence clone() {
        Sequence result;

        result = new Sequence();
        for (Directions layer : layers) {
            result.layers.add(layer.clone());
        }
        return result;
    }

    public Object origin() {
        return merged().origin;
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
        raw.putAll(Json.toStringMap((ObjectNode) helmObject.get("config"), Collections.EMPTY_LIST));
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
        values = freemarker.eval(prev, merged(), toolkit.scripts);
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
                    toolkit.chart(merged().chartOpt).reference);
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
                try {
                    layer.addMerged(null, result); // TODO
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }

        }
        return result;
    }

    private void removePrivate(Diff result) {
        for (Direction direction : merged().directions.values()) {
            if (direction.priv) {
                result.remove(direction.name);
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
}
