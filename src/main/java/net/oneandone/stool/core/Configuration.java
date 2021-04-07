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
import net.oneandone.stool.directions.Script;
import net.oneandone.stool.directions.Toolkit;
import net.oneandone.stool.directions.Variable;
import net.oneandone.stool.directions.Runtime;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.util.Diff;
import net.oneandone.stool.util.Expire;
import net.oneandone.stool.util.Json;
import net.oneandone.stool.util.Pair;
import net.oneandone.stool.util.Tar;
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
 */
public class Configuration {
    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    public static final String WORKING_VALUE = "_working";
    private static final String DIRECTIONS_VALUE = "_directions";

    public static Configuration create(Toolkit toolkit, Directions directions, Map<String, String> values) throws IOException {
        return create(toolkit, directions, Directions.configDirections(values));
    }

    private static Configuration create(Toolkit toolkit, Directions directions, Directions config) throws IOException {
        List<Directions> result;
        Directions chartLayer;

        result = new ArrayList<>();
        result.add(config);
        result.addAll(directions.createLayers(toolkit));
        chartLayer = chartLayer(result);
        result.add(toolkit.chart(chartLayer.chartOpt).directions.clone());
        return new Configuration(result);
    }

    public static Configuration loadAndEat(ObjectNode helmConfig) throws IOException {
        List<Directions> result;

        result = new ArrayList<>();
        for (JsonNode entry : helmConfig.remove(DIRECTIONS_VALUE)) {
            result.add(Directions.load((ObjectNode) entry));
        }
        return new Configuration(result);
    }

    //--

    // config is first; directions without base
    private final List<Directions> layers;

    private final Directions config;
    private final Directions instance;
    private final Directions chart;
    private final Directions root;


    private Configuration(List<Directions> layers) {
        if (layers.size() < 3) {
            throw new IllegalStateException(layers.toString());
        }
        this.layers = layers;
        this.config = layers.get(0);
        this.instance = layers.get(1);
        this.chart = chartLayer(layers);
        this.root = layers.get(layers.size() - 1);
    }

    public String origin() {
        return instance.origin;
    }

    public Configuration withDirections(Toolkit toolkit, Directions directions) throws IOException {
        return Configuration.create(toolkit, directions, config.clone());
    }

    public Configuration withConfig(Map<String, String> overrides) {
        Configuration result;

        result = clone();
        result.config.setValues(overrides);
        return result;
    }

    public Configuration clone() {
        List<Directions> next;

        next = new ArrayList<>();
        for (Directions layer : layers) {
            next.add(layer.clone());
        }
        return new Configuration(next);
    }

    public Set<String> names() {
        Set<String> result;

        result = new HashSet<>();
        for (Directions layer : layers) {
            result.addAll(layer.directions.keySet());
        }
        return result;
    }

    //--

    private static Directions chartLayer(List<Directions> layers) {
        for (Directions d : layers) {
            if (d.chartOpt != null) {
                return d;
            }
        }
        throw new ArgumentException("no chart: " + layers);
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

    //--

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
        return chart.chartOpt + ":" + chart.chartVersionOpt;
    }

    public Pair layerAndExpression(Variable variable) {
        Directions d;
        Direction one;

        d = exprLayer(variable.name);
        if (d == null) {
            // TODO replicas in stool chart ...
            return new Pair(null, null);
        } else {
            one = d.get(variable.name);
            return new Pair(d.subject,  one.expression);
        }
    }

    public ArrayNode toArray(ObjectMapper yaml) {
        ArrayNode result;

        result = yaml.createArrayNode();
        for (Directions layer : layers) {
            result.add(layer.toObject(yaml));
        }
        return result;
    }

    public FileNode createValuesFile(ObjectMapper yaml, World world, Map<String, String> actuals, String workingTarOpt)
            throws IOException {
        ObjectNode dest;
        Expire expire;
        FileNode file;
        String str;

        dest = yaml.createObjectNode();
        for (Map.Entry<String, String> entry : actuals.entrySet()) {
            dest.put(entry.getKey(), entry.getValue());
        }

        dest.set(DIRECTIONS_VALUE, toArray(yaml));
        if (workingTarOpt != null) {
            dest.put(WORKING_VALUE, workingTarOpt);
        }

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

    public Map<String, String> eval(Toolkit toolkit, String stage, String fqdn, FileNode workdir, Map<String, String> prev, Runtime runtime)
            throws IOException {
        Freemarker freemarker;
        Map<String, Direction> execMap;

        verify();
        execMap = new HashMap<>();
        for (String name: names()) {
            execMap.put(name, exprLayer(name).get(name));
        }
        freemarker = toolkit.freemarker(stage, fqdn, workdir);
        return freemarker.eval(prev, execMap.values(), Script.scanOpt(toolkit.scripts), runtime);
    }

    private void verify() {
        Set<String> extras;
        List<Direction> lst;

        extras = names();
        extras.removeAll(root.directions.keySet());
        for (String name : extras) {
            lst = sequence(name);
            if (!lst.get(lst.size() - 1).extra) {
                throw new ArgumentException("missing extra modifier for extra direction: " + name);
            }
        }
    }

    private List<Direction> sequence(String name) {
        List<Direction> result;
        Direction d;

        result = new ArrayList<>();
        for (Directions layer : layers) {
            d = layer.directions.get(name);
            if (d != null) {
                result.add(d);
            }
        }
        return result;
    }

    public Diff helm(Engine engine, String kubeContext, LocalSettings localSettings, String name, boolean upgrade, boolean dryrun, List<String> allowOpt,
                     Map<String, String> prev, String prevWorking) throws IOException {
        Toolkit toolkit;
        FileNode valuesFile;
        Map<String, String> values;
        Diff result;
        Diff forbidden;
        FileNode working;

        toolkit = localSettings.toolkit();
        LOGGER.info("chart: " + chartString());
        working = Tar.toDir(localSettings.world, prevWorking);
        values = eval(toolkit, name, localSettings.fqdn, working, prev, localSettings.runtime(engine, working));
        result = Diff.diff(prev, values);
        if (allowOpt != null) {
            forbidden = result.withoutKeys(allowOpt);
            if (!forbidden.isEmpty()) {
                throw new IOException("change is forbidden:\n" + forbidden);
            }
        }
        removePrivate(result);
        valuesFile = createValuesFile(localSettings.yaml, localSettings.world, values, Tar.fromDirOpt(working));
        working.deleteTree();
        try {
            LOGGER.info("values: " + valuesFile.readString());
            helm(dryrun, kubeContext,
                    localSettings.home, upgrade ? "upgrade" : "install", "--debug", "--values", valuesFile.getAbsolute(), name,
                    toolkit.chart(chart.chartOpt).reference);
            return result;
        } finally {
            valuesFile.deleteFile();
        }
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
}
