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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Clazz {
    public static final String HELM_CLASS = "helmClass";

    public static Clazz loadBase(ObjectMapper yaml, FileNode dir) throws IOException {
        Map.Entry<String, JsonNode> entry;
        String nameAndChart;
        Clazz result;
        String name;
        ValueType old;
        ValueType n;
        ObjectNode obj;
        Iterator<Map.Entry<String, JsonNode>> iter;

        try (Reader src = dir.join("values.yaml").newReader()) {
            obj = (ObjectNode) yaml.readTree(src);
        }
        nameAndChart = dir.getName();
        result = new Clazz("TODO", "TODO", nameAndChart, nameAndChart, "TODO");
        iter = obj.fields();
        obj = null;
        while (iter.hasNext()) {
            entry = iter.next();
            name = entry.getKey();
            if (name.equals("class")) {
                obj = (ObjectNode) entry.getValue();
            } else {
                result.add(new ValueType(entry.getKey(), false, false, null, entry.getValue().asText()));
            }
        }
        if (obj != null) {
            iter = obj.fields();
            while (iter.hasNext()) {
                entry = iter.next();
                name = entry.getKey();
                n = ValueType.forYaml(entry.getKey(), entry.getValue());
                old = result.values.get(name);
                if (old != null && !old.value.isEmpty() && n.value.isEmpty()) {
                    n = n.withValue(old.value);
                }
                result.values.put(name, n);
            }
        }
        return result;
    }

    public static Clazz load(Map<String, Clazz> existing, String origin, String author, ObjectNode clazz) throws IOException {
        Iterator<Map.Entry<String, JsonNode>> values;
        Map.Entry<String, JsonNode> entry;
        String extendz;
        Clazz base;
        Clazz derived;
        String name;

        name = Json.string(clazz, "name");
        extendz = Json.string(clazz, "extends");
        base = existing.get(extendz);
        if (base == null) {
            throw new IOException("class not found: " + extendz);
        }
        derived = base.derive(origin, author, name);
        values = clazz.get("values").fields();
        while (values.hasNext()) {
            entry = values.next();
            derived.define(ValueType.forYaml(entry.getKey(), entry.getValue()));
        }
        return derived;
    }

    public static Clazz forTest(String name, String... nameValues) {
        Clazz result;

        result = new Clazz("synthetic", null, name, "unusedChart", "noVersion");
        for (int i = 0; i < nameValues.length; i += 2) {
            result.add(new ValueType(nameValues[i], false, false, null, nameValues[i + 1]));
        }
        return result;
    }

    //--

    // metadata
    public final String origin;
    public final String author; // null (from file), LIB, or image author

    public final String name;
    public final String chart;
    public final String chartVersion;
    public final Map<String, ValueType> values;

    private Clazz(String origin, String author, String name, String chart, String chartVersion) {
        this.origin = origin;
        this.author = author;
        this.name = name;
        this.chart = chart;
        this.chartVersion = chartVersion;
        this.values = new LinkedHashMap<>();
    }

    public void add(ValueType value) {
        values.put(value.name, value);
    }

    public void setValues(Map<String, String> clientValues) {
        String key;
        ValueType old;

        for (Map.Entry<String, String> entry : clientValues.entrySet()) {
            key = entry.getKey();
            old = values.get(key);
            if (old == null) {
                throw new ArgumentException("unknown value: " + key);
            }
            values.put(name, new ValueType(key, false, false, old.doc, entry.getValue()));
        }
    }

    public void checkNotAbstract() throws IOException {
        List<String> names;

        names = new ArrayList<>();
        for (ValueType value : values.values()) {
            if (value.abstrct) {
                names.add(value.name);
            }
        }
        if (!names.isEmpty()) {
            throw new IOException("class " + name + " has abstract value(s): " + names);
        }
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
        node.set("origin", new TextNode(origin));  // TODO: saved only, not loaded
        if (author != null) {
            node.set("author", new TextNode(author)); // TODO: saved only, not loaded
        }
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

    public Clazz derive(String derivedOrigin, String derivedAuthor, String withName) {
        Clazz result;

        result = new Clazz(derivedOrigin, derivedAuthor, withName, chart, chartVersion);
        for (ValueType value : values.values()) {
            result.add(value);
        }
        return result;
    }

    public void define(ValueType value) throws IOException {
        ValueType old;

        old = values.get(value.name);
        if (old != null && old.doc != null) {
            value = value.withDoc(old.doc);
        }
        /* TODO: if (!values.containsKey(value.name)) {
            throw new IOException("unknown value: " + value.name);
        }*/
        values.put(value.name, value);
    }

    public FileNode createValuesFile(ObjectMapper mapper, Expressions builder) throws IOException {
        ObjectNode dest;
        Expire expire;
        FileNode file;

        dest = mapper.createObjectNode();
        for (Map.Entry<String, String> entry : builder.eval(this).entrySet()) {
            dest.put(entry.getKey(), entry.getValue());
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
}
