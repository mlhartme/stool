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

    public static Clazz load(ObjectMapper yaml, Map<String, Clazz> existing, String origin, String author, ObjectNode clazz, FileNode charts) throws IOException {
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
            derived = new Clazz(origin, author, name, chart);
            derived.addChartValues(yaml, charts.join(chart, "values.yaml"));
        } else {
            base = existing.get(extendz);
            if (base == null) {
                throw new IOException("class not found: " + extendz);
            }
            derived = base.derive(origin, author, name);
        }
        values = clazz.get("values").fields();
        while (values.hasNext()) {
            entry = values.next();
            derived.define(ValueType.forYaml(entry.getKey(), entry.getValue()));
        }

        return derived;
    }

    private void addChartValues(ObjectMapper yaml, FileNode valuesYaml) throws IOException {
        ObjectNode loaded;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;

        try (Reader src = valuesYaml.newReader()) {
            loaded = (ObjectNode) yaml.readTree(src);
        }
        iter = loaded.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            add(new ValueType(entry.getKey(), false, false, entry.getValue().asText()));
        }
    }

    public static Clazz forTest(String name, String... nameValues) {
        Clazz result;

        result = new Clazz("synthetic", null, name, "unusedChart");
        for (int i = 0; i < nameValues.length; i += 2) {
            result.add(new ValueType(nameValues[i], false, false, nameValues[i + 1]));
        }
        return result;
    }

    //--

    // metadata
    public final String origin;
    public final String author; // null (from file), LIB, or image author

    public final String name;
    public final String chart;
    public final Map<String, ValueType> values;

    private Clazz(String origin, String author, String name, String chart) {
        this.origin = origin;
        this.author = author;
        this.name = name;
        this.chart = chart;
        this.values = new LinkedHashMap<>();
    }

    public void add(ValueType value) {
        values.put(value.name, value);
    }

    public void setValues(Map<String, String> clientValues) {
        ValueType v;

        for (Map.Entry<String, String> entry : clientValues.entrySet()) {
            v = new ValueType(entry.getKey(), false, false, entry.getValue());
            if (values.put(v.name, v) == null) {
                throw new ArgumentException("unknown value: " + v.name);
            }
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

        result = new Clazz(derivedOrigin, derivedAuthor, withName, chart);
        for (ValueType value : values.values()) {
            result.add(value);
        }
        return result;
    }

    public void define(ValueType value) throws IOException {
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
