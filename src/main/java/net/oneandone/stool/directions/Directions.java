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
        result = new Directions(name + "-chart", "chart '" + name + '"', "TODO", name, tagFile.exists() ? tagFile.readString().trim() : "unknown");
        result.defineBaseAll(loaded.fields());
        return result;
    }

    private static final String DIRECTIONS = "DIRECTIONS";
    private static final String EXTENDS = "EXTENDS";
    private static final String CHART = "CHART";
    private static final String CHART_VERSION = "CHART-VERSION";
    private static final String ORIGIN = "ORIGIN";
    private static final String AUTHOR = "AUTHOR";

    /** from inline, label or file; always extends */
    public static Directions loadLiteral(Map<String, Directions> existing, String origin, String author, ObjectNode directions) throws IOException {
        Map<String, JsonNode> raw;
        Directions base;
        Directions derived;
        String subject;
        List<Directions> bases;

        raw = loadRaw(directions);
        subject = eatString(raw, DIRECTIONS);
        bases = new ArrayList();
        for (String baseName : stringListOpt(raw.remove(EXTENDS))) {
            base = existing.get(baseName);
            if (base == null) {
                throw new IOException("base directions not found: " + baseName);
            }
            bases.add(base);
        }
        derived = extend(origin, author, subject, bases);
        for (Map.Entry<String, JsonNode> entry : raw.entrySet()) {
            derived.define(Direction.forYaml(entry.getKey(), entry.getValue()));
        }
        return derived;
    }

    private static String eatString(Map<String, JsonNode> raw, String name) throws IOException {
        String result;

        result = eatStringOpt(raw, name);
        if (result == null) {
            throw new IOException("missing " + name);
        }
        return result;
    }

    private static String eatStringOpt(Map<String, JsonNode> raw, String name) {
        JsonNode node;

        node = raw.remove(name);
        return node == null ? null : node.asText();
    }

    private static List<String> stringListOpt(JsonNode arrayOpt) {
        List<String> result;

        result = new ArrayList<>();
        if (arrayOpt != null && !arrayOpt.isNull()) {
            if (arrayOpt.isTextual()) {
                result.add(arrayOpt.asText());
            } else if (arrayOpt.isArray()) {
                for (JsonNode entry : arrayOpt) {
                    result.add(entry.asText());
                }
            } else {
                throw new ArgumentException("string or array expected: " + arrayOpt);
            }
        }
        return result;
    }

    private static Map<String, JsonNode> loadRaw(ObjectNode directions) {
        Map<String, JsonNode> result;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;

        result = new LinkedHashMap<>();
        iter = directions.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public static Directions loadHelm(ObjectNode directions) throws IOException {
        Map<String, JsonNode> raw;
        Directions result;

        raw = loadRaw(directions);
        // chart + version are mandatory here because a stage was created with them:
        result = new Directions(eatString(raw, DIRECTIONS), eatStringOpt(raw, ORIGIN), eatStringOpt(raw, AUTHOR), eatString(raw, CHART), eatString(raw, CHART_VERSION));
        for (Map.Entry<String, JsonNode> entry : raw.entrySet()) {
            result.defineBase(Direction.forYaml(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public static Directions forTest(String name, String... nameValues) {
        Directions result;

        result = new Directions(name, "synthetic", null, "unusedChart", "noVersion");
        for (int i = 0; i < nameValues.length; i += 2) {
            result.defineBase(new Direction(nameValues[i], nameValues[i + 1]));
        }
        return result;
    }

    //--

    public final String subject;
    public final String origin;
    public final String author; // null (from file), LIB, or image author
    public final String chartOpt;
    public final String chartVersionOpt;
    public final Map<String, Direction> directions;

    private Directions(String subject, String origin, String author, String chartOpt, String chartVersionOpt) {
        this.subject = subject;
        this.origin = origin;
        this.author = author;
        this.chartOpt = chartOpt;
        this.chartVersionOpt = chartVersionOpt;
        this.directions = new LinkedHashMap<>();
    }

    public void defineBaseAll(Iterator<Map.Entry<String, JsonNode>> iter) {
        Map.Entry<String, JsonNode> entry;

        while (iter.hasNext()) {
            entry = iter.next();
            defineBase(Direction.forYaml(entry.getKey(), entry.getValue()));
        }
    }
    public void defineBase(Direction direction) {
        if (directions.put(direction.name, direction) != null) {
            System.out.println("TODO duplicate: " + direction.name);
        }
    }

    public void define(Direction direction) {
        Direction old;

        old = directions.get(direction.name);
        if (old != null) {
            if (old.privt) {
                throw new IllegalStateException("you cannot override private direction: " + direction.name);
            }
            if (direction.extra) {
                throw new IllegalStateException("extra direction overrides base direction: " + direction.name);
            }
            if (old.doc != null && direction.doc == null) {
                direction = direction.withDoc(old.doc);
            }
            if (!old.expression.isEmpty() && direction.expression.isEmpty()) {
                direction = direction.withExpression(old.expression);
            }
        } else {
            if (chartOpt == null) {
                // TODO: mixin
            } else {
                if (!direction.extra) {
                    throw new IllegalStateException("extra direction expected: " + direction.name);
                }
            }
        }
        directions.put(direction.name, direction);
    }

    public void setValues(Map<String, String> values) {
        String key;
        Direction old;

        for (Map.Entry<String, String> entry : values.entrySet()) {
            key = entry.getKey();
            old = directions.get(key);
            if (old == null) {
                throw new ArgumentException("unknown direction: " + key);
            }
            directions.put(key, new Direction(key, old.privt, false, old.doc, entry.getValue()));
        }
    }

    public int size() {
        return directions.size();
    }

    public Direction get(String n) {
        Direction result;

        result = directions.get(n);
        if (result == null) {
            throw new IllegalStateException(n);
        }
        return result;
    }

    public ObjectNode toObject(ObjectMapper yaml) {
        ObjectNode node;

        node = yaml.createObjectNode();
        node.set(DIRECTIONS, new TextNode(DIRECTIONS));
        node.set(ORIGIN, new TextNode(origin));
        if (author != null) {
            node.set(AUTHOR, new TextNode(author));
        }
        if (chartOpt != null) {
            node.set(CHART, new TextNode(chartOpt));
            if (chartVersionOpt != null) {
                node.set(CHART_VERSION, new TextNode(chartVersionOpt));
            }
        }
        for (Direction direction : directions.values()) {
            node.set(direction.name, direction.toObject(yaml));
        }
        return node;
    }

    public static Directions extend(String derivedOrigin, String derivedAuthor, String withSubject, List<Directions> bases) throws IOException {
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
        result = new Directions(withSubject, derivedOrigin, derivedAuthor, chartOpt, chartVersionOpt);
        for (Directions base : bases) {
            for (Direction direction : base.directions.values()) {
                result.defineBase(direction);
            }
        }
        return result;
    }
}
