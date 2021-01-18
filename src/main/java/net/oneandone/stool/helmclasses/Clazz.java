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
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
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
    public static final String HELM_CLASS = "helmClass";

    public static Map<String, Clazz> loadAll(ObjectMapper yaml, FileNode root) throws IOException {
        Iterator<JsonNode> classes;
        Map<String, Clazz> result;
        Clazz clazz;

        try (Reader src = root.join("classes.yaml").newReader()) {
            classes = yaml.readTree(src).elements();
        }
        result = new HashMap<>();
        while (classes.hasNext()) {
            clazz = load(yaml, result, (ObjectNode) classes.next(), root);
            if (result.put(clazz.name, clazz) != null) {
                throw new IOException("duplicate class: " + clazz.name);
            }
        }
        return result;
    }

    public static Clazz load(ObjectMapper yaml, Map<String, Clazz> existing, ObjectNode clazz, FileNode root) throws IOException {
        Iterator<Map.Entry<String, JsonNode>> values;
        Map.Entry<String, JsonNode> entry;
        String extendz;
        String chart;
        Clazz base;
        Clazz derived;
        String name;

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
            base = existing.get(extendz);
            if (base == null) {
                throw new IOException("class not found: " + extendz);
            }
            derived = base.derive(name);
        }
        values = clazz.get("values").fields();
        while (values.hasNext()) {
            entry = values.next();
            derived.define(ValueType.forYaml(entry.getKey(), entry.getValue()));
        }

        return derived;
    }

    private static Map<String, ValueType> loadChartValues(ObjectMapper yaml, FileNode valuesYaml) throws IOException {
        ObjectNode values;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;
        Map<String, ValueType> result;

        try (Reader src = valuesYaml.newReader()) {
            values = (ObjectNode) yaml.readTree(src);
        }
        result = new HashMap();
        iter = values.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            result.put(entry.getKey(), new ValueType(entry.getKey(), false, false, entry.getValue().asText()));
        }
        return result;
    }

    public static Clazz forTest(String name, String... nameValues) {
        Map<String, ValueType> map;
        ValueType v;

        map = new HashMap<>();
        for (int i = 0; i < nameValues.length; i += 2) {
            v = new ValueType(nameValues[i], false, false, nameValues[i + 1]);
            if (map.put(v.name, v) != null) {
                throw new IllegalArgumentException("duplicate field: " + v.name);
            }
        }
        return new Clazz(name, "unusedChart", map);
    }

    //--

    public final String name;
    public final String chart;
    public final Map<String, ValueType> values;

    private Clazz(String name, String chart, Map<String, ValueType> values) {
        this.name = name;
        this.chart = chart;
        this.values = values;
    }

    public ValueType get(String value) {
        ValueType result;

        result = values.get(value);
        if (result == null) {
            throw new IllegalStateException(value);
        }
        return result;
    }

    public ObjectNode toObject(ObjectMapper yaml) {
        ObjectNode node;
        ObjectNode v;

        node = yaml.createObjectNode();
        node.set("name", new TextNode(name));
        node.set("chart", new TextNode(chart));
        v = yaml.createObjectNode();
        node.set("values", v);
        for (ValueType value : values.values()) {
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

    public void define(ValueType value) throws IOException {
        /* TODO: if (!values.containsKey(value.name)) {
            throw new IOException("unknown value: " + value.name);
        }*/
        values.put(value.name, value);
    }

    public FileNode createValuesFile(ObjectMapper mapper, Expressions builder, Map<String, String> clientValues, Map<String, Object> initial) throws IOException {
        ObjectNode dest;
        String key;
        Expire expire;
        FileNode file;

        dest = mapper.createObjectNode();
        for (Map.Entry<String, Object> entry : initial.entrySet()) {
            dest.set(entry.getKey(), toJson(entry.getValue()));
        }
        // TODO: overwrites ...
        for (Map.Entry<String, String> entry : builder.eval(this).entrySet()) {
            dest.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : clientValues.entrySet()) {
            key = entry.getKey();
            if (!this.values.containsKey(key)) {
                throw new ArgumentException("unknown value: " + key);
            }
            dest.put(key, entry.getValue());
        }

        dest.set(HELM_CLASS, toObject(mapper));

        // normalize expire
        expire = Expire.fromHuman(Json.string(dest, Type.VALUE_EXPIRE, Expire.fromNumber(builder.configuration.defaultExpire).toString()));
        if (expire.isExpired()) {
            throw new ArgumentException("stage expired: " + expire);
        }
        dest.put(Type.VALUE_EXPIRE, expire.toString());

        file = builder.world.getTemp().createTempFile().writeString(dest.toPrettyString());
        return file;
    }

    private static JsonNode toJson(Object obj) {
        if (obj == null) {
            return NullNode.getInstance();
        }
        if (obj instanceof String) {
            return new TextNode((String) obj);
        }
        if (obj instanceof Integer) {
            return new IntNode((Integer) obj);
        }
        if (obj instanceof Boolean) {
            return BooleanNode.valueOf((Boolean) obj);
        }
        throw new IllegalStateException(obj.getClass().toString());
    }
}
