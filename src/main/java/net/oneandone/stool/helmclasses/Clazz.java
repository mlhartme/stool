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
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.Type;
import net.oneandone.stool.util.Expire;
import net.oneandone.stool.util.Json;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Clazz {
    public static Map<String, Clazz> loadAll(FileNode root) throws IOException {
        ObjectMapper yaml;
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

        yaml = new ObjectMapper(new YAMLFactory());
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

    public Clazz derive(String withName) {
        return new Clazz(withName, chart, new HashMap<>(values));
    }

    public void define(Value value) throws IOException {
        if (!values.containsKey(value.name)) {
            throw new IOException("unknown value: " + value.name);
        }
        values.put(value.name, value);
    }

    public FileNode createValuesFile(Expressions builder, Map<String, String> clientValues, Map<String, Object> dest) throws IOException {
        String key;
        Expire expire;
        FileNode file;

        for (Value field : this.values.values()) {
            if (field != null) {
                dest.put(field.name, builder.eval(field.macro));
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
        expire = Expire.fromHuman((String) dest.getOrDefault(Type.VALUE_EXPIRE, Integer.toString(builder.configuration.defaultExpire)));
        if (expire.isExpired()) {
            throw new ArgumentException("stage expired: " + expire);
        }
        dest.put(Type.VALUE_EXPIRE, expire.toString()); // normalize

        file = builder.world.getTemp().createTempFile();
        try (PrintWriter v = new PrintWriter(file.newWriter())) {
            for (Map.Entry<String, Object> entry : dest.entrySet()) {
                v.println(entry.getKey() + ": " + toJson(entry.getValue()));
            }
        }
        return file;
    }

    private static String toJson(Object obj) {
        if (obj instanceof String) {
            return "\"" + obj + '"';
        } else {
            return obj.toString(); // ok für boolean and integer
        }
    }
}
