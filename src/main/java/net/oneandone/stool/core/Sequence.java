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

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mostly a name for an expression, can be evaluated. Immutable. */
public class Sequence {
    private static final Logger LOGGER = LoggerFactory.getLogger(Sequence.class);

    private static final String MERGED_INSTANCE_DIRECTIONS_VALUE = "_merge_instance_directions";
    private static final String CONFIG_DIRECTIONS_VALUE = "_config_directions";

    public static Sequence loadAndEat(ObjectNode helmConfig) throws IOException {
        Directions merged;
        Directions config;

        merged = Directions.loadHelm((ObjectNode) helmConfig.remove(MERGED_INSTANCE_DIRECTIONS_VALUE));
        config = Directions.loadLiteral(merged.origin, merged.author, (ObjectNode) helmConfig.remove(CONFIG_DIRECTIONS_VALUE));
        return new Sequence(merged, config);
    }

    //--

    private final Directions merged;
    private final Directions config;

    public Sequence(Directions merged, Directions config) {
        this.merged = merged;
        this.config = config;
    }

    public String chartString() {
        return merged.chartOpt + ":" + merged.chartVersionOpt;
    }

    public boolean fixed(String name) {
        return config.directions.containsKey(name);
    }

    public String value(Variable variable) {
        String result;

        result = variable.get();
        if (!fixed(variable.name)) {
            result = result + " # " + merged.get(variable.name).expression;
        }
        return result;
    }

    public ObjectNode toObject(ObjectMapper yaml) {
        ObjectNode result;

        result = yaml.createObjectNode();
        result.set("instance", merged.toObject(yaml));
        result.set("config", config.toObject(yaml));
        return result;
    }


    public Sequence nextSequence(LocalSettings localSettings, DirectionsRef directionsRefOpt) throws IOException {
        Directions directions;

        directions = directionsRefOpt.resolve(localSettings).merged(localSettings.toolkit());
        return new Sequence(directions, config.clone());
    }

    public Sequence nextSequence(Map<String, String> overrides) {
        Directions nextConfig;

        nextConfig = config.clone();
        nextConfig.setValues(overrides);
        return new Sequence(merged.clone(), nextConfig);
    }

    public Directions configMerged(Toolkit toolkit) throws IOException {
        Directions result;

        result = merged.clone();
        config.addMerged(toolkit, result);
        return result;
    }

    public Object origin() {
        return merged.origin;
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

        dest.set(MERGED_INSTANCE_DIRECTIONS_VALUE, merged.toObject(yaml));
        dest.set(CONFIG_DIRECTIONS_VALUE, config.toObject(yaml));

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
        Directions directions;
        Map<String, Object> raw;
        Map<String, Variable> result;
        String key;
        Direction d;

        directions = merged; // config just adds values, so it's safe to pass instance only here
        raw = Json.toStringMap((ObjectNode) helmObject.get("chart").get("values"), Collections.emptyList());
        raw.putAll(Json.toStringMap((ObjectNode) helmObject.get("config"), Collections.EMPTY_LIST));
        result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            key = entry.getKey();
            d = directions.get(key);
            result.put(key, new Variable(d.name, d.priv, d.doc, entry.getValue().toString()));
        }
        return result;
    }

    public void removePrivate(Diff result) {
        for (Direction direction : merged.directions.values()) {
            if (direction.priv) {
                result.remove(direction.name);
            }
        }
    }

    //--

    public Diff helm(String kubeContext, LocalSettings localSettings, String name, boolean upgrade, boolean dryrun, List<String> allowOpt,
                            Map<String, String> prev)
            throws IOException {
        Toolkit toolkit;
        Freemarker freemarker;
        FileNode valuesFile;
        Map<String, String> values;
        Diff result;
        Diff forbidden;

        toolkit = localSettings.toolkit();
        LOGGER.info("chart: " + chartString());
        freemarker = toolkit.freemarker(localSettings.getLib(), name, localSettings.fqdn);
        values = freemarker.eval(prev, configMerged(toolkit), toolkit.scripts);
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
                    toolkit.chart(merged.chartOpt).reference);
            return result;
        } finally {
            valuesFile.deleteFile();
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
