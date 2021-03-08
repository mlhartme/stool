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
package net.oneandone.stool.directions;

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

public class Directions {
    // this value is added to track the stage directions used
    public static final String DIRECTIONS_VALUE = "_directions";

    public static Directions loadStageDirectionsBase(World world, ObjectMapper yaml) throws IOException {
        try (Reader src = world.resource("stage.yaml").newReader()) {
            return Directions.loadLiteral(Collections.emptyMap(), "root", "stool", (ObjectNode) yaml.readTree(src));
        }
    }

    /** loads the directions implicitly defined by a chart */
    public static Directions loadChartDirections(ObjectMapper yaml, String name, FileNode chart) throws IOException {
        Directions result;
        ObjectNode loaded;
        FileNode tagFile;

        try (Reader src = chart.join("values.yaml").newReader()) {
            loaded = (ObjectNode) yaml.readTree(src);
        }
        tagFile = Helm.tagFile(chart);
        result = new Directions("chart '" + name + '"', "TODO", name + "-chart", name, tagFile.exists() ? tagFile.readString().trim() : "unknown");
        result.defineBaseAll(loaded.fields());
        return result;
    }

    /** from inline, label or file; always extends */
    public static Directions loadLiteral(Map<String, Directions> existing, String origin, String author, ObjectNode directions) throws IOException {
        Directions base;
        Directions derived;
        String name;
        List<Directions> bases;

        name = Json.string(directions, "name");
        bases = new ArrayList();
        for (String baseName : Json.stringListOpt(directions, "extends")) {
            base = existing.get(baseName);
            if (base == null) {
                throw new IOException("base directions not found: " + baseName);
            }
            bases.add(base);
        }
        derived = extend(origin, author, name, bases);
        derived.defineAll(directions.get("properties").fields());
        return derived;
    }

    public static Directions loadHelm(ObjectNode directions) {
        Directions result;
        String name;

        name = Json.string(directions, "name");
        result = new Directions(Json.stringOpt(directions, "origin"), Json.stringOpt(directions, "author"), name,
                // chart + version are mandatory here because a stage was created with them:
                Json.string(directions, "chart"), Json.string(directions, "chartVersion"));
        result.defineBaseAll(directions.get("properties").fields());
        return result;
    }

    public static Directions forTest(String name, String... nameValues) {
        Directions result;

        result = new Directions("synthetic", null, name, "unusedChart", "noVersion");
        for (int i = 0; i < nameValues.length; i += 2) {
            result.defineBase(new Direction(nameValues[i], nameValues[i + 1]));
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
    public final Map<String, Direction> properties;

    private Directions(String origin, String author, String name, String chartOpt, String chartVersionOpt) {
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
            defineBase(Direction.forYaml(entry.getKey(), entry.getValue()));
        }
    }
    public void defineBase(Direction value) {
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
            define(Direction.forYaml(key, entry.getValue()));
        }
    }

    public void define(Direction property) {
        Direction old;

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
        Direction old;

        for (Map.Entry<String, String> entry : values.entrySet()) {
            key = entry.getKey();
            old = properties.get(key);
            if (old == null) {
                throw new ArgumentException("unknown property: " + key);
            }
            properties.put(key, new Direction(key, old.privt, false, old.doc, entry.getValue()));
        }
    }

    public int size() {
        return properties.size();
    }

    public Direction get(String value) {
        Direction result;

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
        for (Direction property : properties.values()) {
            p.set(property.name, property.toObject(yaml));
        }
        return node;
    }

    public static Directions extend(String derivedOrigin, String derivedAuthor, String withName, List<Directions> bases) throws IOException {
        String chartOpt;
        String chartVersionOpt;
        Directions result;

        chartOpt = null;
        chartVersionOpt = null;
        for (Directions base : bases) {
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
        result = new Directions(derivedOrigin, derivedAuthor, withName, chartOpt, chartVersionOpt);
        for (Directions base : bases) {
            for (Direction property : base.properties.values()) {
                result.defineBase(property);
            }
        }
        return result;
    }
}
