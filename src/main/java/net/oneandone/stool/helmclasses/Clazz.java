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
package net.oneandone.stool.helmclasses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.Type;
import net.oneandone.stool.util.Expire;
import net.oneandone.stool.util.Json;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Clazz {
    public static Map<String, Clazz> loadAll(ObjectMapper yaml, FileNode root) throws IOException {
        Iterator<JsonNode> classes;
        Iterator<Map.Entry<String, JsonNode>> values;
        Map.Entry<String, JsonNode> entry;
        Map<String, Clazz> result;
        ObjectNode clazz;
        String extendz;
        String chart;
        Clazz base;
        Clazz derived;
        String name;

        try (Reader src = root.join("classes.yaml").newReader()) {
            classes = yaml.readTree(src).elements();
        }
        result = new HashMap<>();
        while (classes.hasNext()) {
            clazz = (ObjectNode) classes.next();
            name = Json.string(clazz, "name");
            chart = Json.stringOpt(clazz, "chart");
            extendz = Json.stringOpt(clazz, "extends");
            if (chart == null && extendz == null) {
                throw new IOException("chart or extends expected");
            }
            if (chart != null && extendz != null) {
                throw new IOException("chart and extends cannot be combined");
            }
            if (chart != null) {
                derived = new Clazz(name, chart, loadChartValues(yaml, root.join(chart, "values.yaml")));
            } else {
                base = result.get(extendz);
                if (base == null) {
                    throw new IOException("class not found: " + extendz);
                }
                derived = base.derive(name);
            }
            result.put(derived.name, derived);
            values = clazz.get("values").fields();
            while (values.hasNext()) {
                entry = values.next();
                derived.define(Value.forYaml(entry.getKey(), entry.getValue()));
            }
        }
        return result;
    }

    private static Map<String, Value> loadChartValues(ObjectMapper yaml, FileNode valuesYaml) throws IOException {
        ObjectNode values;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;
        Map<String, Value> result;

        try (Reader src = valuesYaml.newReader()) {
            values = (ObjectNode) yaml.readTree(src);
        }
        result = new HashMap();
        iter = values.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            result.put(entry.getKey(), null);
        }
        return result;
    }

    //--

    public final String name;
    public final String chart;
    public final Map<String, Value> values;

    private Clazz(String name, String chart, Map<String, Value> values) {
        this.name = name;
        this.chart = chart;
        this.values = values;
    }

    public ObjectNode toObject(ObjectMapper yaml) {
        ObjectNode node;
        ObjectNode v;

        node = yaml.createObjectNode();
        node.set("name", new TextNode(name));
        node.set("chart", new TextNode(chart));
        v = yaml.createObjectNode();
        node.set("values", v);
        for (Value value : values.values()) {
            if (value == null) {
                // ignore
            } else {
                v.set(value.name, value.toObject(yaml));
            }
        }
        return node;
    }

    public Clazz derive(String withName) {
        return new Clazz(withName, chart, new HashMap<>(values));
    }

    public void define(Value value) throws IOException {
        if (!values.containsKey(value.name)) {
            throw new IOException("unknown value: " + value.name);
        }
        values.put(value.name, value);
    }

    public FileNode createValuesFile(ObjectMapper mapper, Expressions builder, Map<String, String> clientValues, Map<String, Object> initial) throws IOException {
        ObjectNode dest;
        String key;
        Expire expire;
        FileNode file;

        dest = mapper.createObjectNode();
        for (Map.Entry<String, Object> entry : initial.entrySet()) {
            dest.put(entry.getKey(), (String) entry.getValue());
        }
        for (Value field : this.values.values()) {
            if (field != null) {
                dest.put(field.name, builder.eval(field.value));
            }
        }
        for (Map.Entry<String, String> entry : clientValues.entrySet()) {
            key = entry.getKey();
            if (!this.values.containsKey(key)) {
                throw new ArgumentException("unknown value: " + key);
            }
            dest.put(key, entry.getValue());
        }

        // normalize expire
        expire = Expire.fromHuman(Json.string(dest, Type.VALUE_EXPIRE, Expire.fromNumber(builder.configuration.defaultExpire).toString()));
        if (expire.isExpired()) {
            throw new ArgumentException("stage expired: " + expire);
        }
        dest.put(Type.VALUE_EXPIRE, expire.toString());

        file = builder.world.getTemp().createTempFile().writeString(dest.toPrettyString());
        return file;
    }
}
