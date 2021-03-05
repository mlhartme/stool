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
package net.oneandone.stool.classes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.util.Json;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Clazz {
    public static final String HELM_CLASS = "helmClass";

    /** loads the class ex- or implicitly defined by a chart */
    public static Clazz loadStageClass(World world, ObjectMapper yaml) throws IOException {
        try (Reader src = world.resource("stage.yaml").newReader()) {
            return Clazz.loadLiteral(Collections.emptyMap(), "root", "stool", (ObjectNode) yaml.readTree(src));
        }
    }

    /** loads the class ex- or implicitly defined by a chart */
    public static Clazz loadChartClass(ObjectMapper yaml, String name, FileNode chart) throws IOException {
        Clazz result;
        ObjectNode loaded;
        FileNode tagFile;

        try (Reader src = chart.join("values.yaml").newReader()) {
            loaded = (ObjectNode) yaml.readTree(src);
        }
        tagFile = Helm.tagFile(chart);
        result = new Clazz("chart '" + name + '"', "TODO", name + "-chart", name, tagFile.exists() ? tagFile.readString().trim() : "unknown");
        result.defineBaseAll(loaded.fields());
        return result;
    }

    /** from inline, label or classes; always extends */
    public static Clazz loadLiteral(Map<String, Clazz> existing, String origin, String author, ObjectNode clazz) throws IOException {
        Clazz base;
        Clazz derived;
        String name;
        List<Clazz> bases;

        name = Json.string(clazz, "name");
        bases = new ArrayList();
        for (String baseName : Json.stringListOpt(clazz, "extends")) {
            base = existing.get(baseName);
            if (base == null) {
                throw new IOException("base class not found: " + baseName);
            }
            bases.add(base);
        }
        derived = extend(origin, author, name, bases);
        derived.defineAll(clazz.get("properties").fields());
        return derived;
    }

    public static Clazz loadHelm(ObjectNode clazz) {
        Clazz result;
        String name;

        name = Json.string(clazz, "name");
        result = new Clazz(Json.stringOpt(clazz, "origin"), Json.stringOpt(clazz, "author"), name,
                // chart + version are mandatory here because a stage was created with them:
                Json.string(clazz, "chart"), Json.string(clazz, "chartVersion"));
        result.defineBaseAll(clazz.get("properties").fields());
        return result;
    }

    public static Clazz forTest(String name, String... nameValues) {
        Clazz result;

        result = new Clazz("synthetic", null, name, "unusedChart", "noVersion");
        for (int i = 0; i < nameValues.length; i += 2) {
            result.defineBase(new Property(nameValues[i], nameValues[i + 1]));
        }
        return result;
    }

    //--

    // metadata
    public final String origin;
    public final String author; // null (from file), LIB, or image author

    public final String name;
    public final String chartOpt;
    public final String chartVersionOpt;
    public final Map<String, Property> properties;

    private Clazz(String origin, String author, String name, String chartOpt, String chartVersionOpt) {
        this.origin = origin;
        this.author = author;
        this.name = name;
        this.chartOpt = chartOpt;
        this.chartVersionOpt = chartVersionOpt;
        this.properties = new LinkedHashMap<>();
    }

    public void defineBaseAll(Iterator<Map.Entry<String, JsonNode>> iter) {
        Map.Entry<String, JsonNode> entry;

        while (iter.hasNext()) {
            entry = iter.next();
            defineBase(Property.forYaml(entry.getKey(), entry.getValue()));
        }
    }
    public void defineBase(Property value) {
        if (properties.put(value.name, value) != null) {
            System.out.println("TODO duplicate: " + value.name);
        }
    }

    public void defineAll(Iterator<Map.Entry<String, JsonNode>> iter) {
        Map.Entry<String, JsonNode> entry;
        String key;

        while (iter.hasNext()) {
            entry = iter.next();
            key = entry.getKey();
            define(Property.forYaml(key, entry.getValue()));
        }
    }

    public void define(Property property) {
        Property old;

        old = properties.get(property.name);
        if (old != null) {
            if (old.privt) {
                throw new IllegalStateException("you cannot override private property: " + property.name);
            }
            if (property.extra) {
                throw new IllegalStateException("extra property overrides base property: " + property.name);
            }
            if (old.doc != null && property.doc == null) {
                property = property.withDoc(old.doc);
            }
            if (!old.function.isEmpty() && property.function.isEmpty()) {
                property = property.withFunction(old.function);
            }
        } else {
            if (chartOpt == null) {
                // TODO: mixin
            } else {
                if (!property.extra) {
                    throw new IllegalStateException("extra value expected: " + property.name);
                }
            }
        }
        properties.put(property.name, property);
    }

    public void setValues(Map<String, String> values) {
        String key;
        Property old;

        for (Map.Entry<String, String> entry : values.entrySet()) {
            key = entry.getKey();
            old = properties.get(key);
            if (old == null) {
                throw new ArgumentException("unknown property: " + key);
            }
            properties.put(key, new Property(key, old.privt, false, old.doc, entry.getValue()));
        }
    }

    public int size() {
        return properties.size();
    }

    public Property get(String value) {
        Property result;

        result = properties.get(value);
        if (result == null) {
            throw new IllegalStateException(value);
        }
        return result;
    }

    public ObjectNode toObject(ObjectMapper yaml) {
        ObjectNode node;
        ObjectNode p;

        node = yaml.createObjectNode();
        node.set("origin", new TextNode(origin));
        if (author != null) {
            node.set("author", new TextNode(author));
        }
        node.set("name", new TextNode(name));
        if (chartOpt != null) {
            node.set("chart", new TextNode(chartOpt));
            if (chartVersionOpt != null) {
                node.set("chartVersion", new TextNode(chartVersionOpt));
            }
        }
        p = yaml.createObjectNode();
        node.set("properties", p);
        for (Property property : properties.values()) {
            p.set(property.name, property.toObject(yaml));
        }
        return node;
    }

    public static Clazz extend(String derivedOrigin, String derivedAuthor, String withName, List<Clazz> bases) throws IOException {
        String chartOpt;
        String chartVersionOpt;
        Clazz result;

        chartOpt = null;
        chartVersionOpt = null;
        for (Clazz base : bases) {
            if (base.chartOpt != null) {
                if (chartOpt == null) {
                    chartOpt = base.chartOpt;
                    chartVersionOpt = base.chartVersionOpt;
                } else {
                    if (!chartOpt.equals(base.chartOpt)) {
                        throw new IOException("charts diverge: " + chartOpt + " vs" + base.chartOpt);
                    }
                    if (!chartVersionOpt.equals(base.chartVersionOpt)) {
                        throw new IOException(chartOpt + ": chart versions diverge: " + chartVersionOpt + " vs" + base.chartVersionOpt);
                    }
                }
            }
        }
        result = new Clazz(derivedOrigin, derivedAuthor, withName, chartOpt, chartVersionOpt);
        for (Clazz base : bases) {
            for (Property property : base.properties.values()) {
                result.defineBase(property);
            }
        }
        return result;
    }
}
