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
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Directions {
    /** loads the directions implicitly defined by a chart */
    public static Directions loadChartDirections(ObjectMapper yaml, String name, String version, FileNode valuesYaml) throws IOException {
        Directions result;
        Iterator<Map.Entry<String, JsonNode>> values;
        Map.Entry<String, JsonNode> entry;

        try (Reader src = valuesYaml.newReader()) {
            values = yaml.readTree(src).fields();
        }
        result = new Directions(name + "-chart", "chart '" + name + '"', "TODO", name, version);
        while (values.hasNext()) {
            entry = values.next();
            result.addNew(Direction.forYaml(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public static final String DIRECTIONS = "DIRECTIONS";
    public static final String EXTENDS = "EXTENDS";
    public static final String CHART = "CHART";
    public static final String CHART_VERSION = "CHART-VERSION";
    public static final String ORIGIN = "ORIGIN";
    public static final String AUTHOR = "AUTHOR";

    public static Directions load(String origin, String author, ObjectNode directions) throws IOException {
        return load(directions).with(origin, author);
    }

    public static Directions load(ObjectNode directions) throws IOException {
        Map<String, JsonNode> raw;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;
        String subject;
        String origin;
        String author;
        String chart;
        String chartVersion;
        List<String> bases;
        Directions result;
        Direction d;

        raw = new LinkedHashMap<>();
        iter = directions.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            raw.put(entry.getKey(), entry.getValue());
        }
        subject = eatString(raw, Directions.DIRECTIONS);
        origin = eatStringOpt(raw, Directions.ORIGIN);
        author = eatStringOpt(raw, Directions.AUTHOR);
        chart = eatStringOpt(raw, Directions.CHART);
        chartVersion = eatStringOpt(raw, Directions.CHART_VERSION);
        bases = stringListOpt(raw.remove(Directions.EXTENDS));
        result = new Directions(subject, origin, author, chart, chartVersion);
        result.bases.addAll(bases);
        for (Map.Entry<String, JsonNode> e : raw.entrySet()) {
            d = Direction.forYaml(e.getKey(), e.getValue());
            if (result.directions.put(d.name, d) != null) {
                throw new ArgumentException("duplicate direction: " + d.name);
            }
        }
        return result;
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

    //--

    public static Directions forTest(String name, String... nameValues) {
        Directions result;

        result = new Directions(name, "synthetic", null, null, null);
        for (int i = 0; i < nameValues.length; i += 2) {
            result.addNew(new Direction(nameValues[i], nameValues[i + 1]));
        }
        return result;
    }

    public static Directions configDirections(Map<String, String> overrides) {
        Directions result;

        result = new Directions("config", "TODO", "TODO", null, null);
        result.setValues(overrides);
        return result;
    }

    //--

    public final String subject;
    public final String origin;
    public final String author; // null (from file), LIB, or image author
    public final String chartOpt;
    public final String chartVersionOpt;
    public final List<String> bases;
    public final Map<String, Direction> directions;

    public Directions(String subject, String origin, String author, String chartOpt, String chartVersionOpt) {
        this.subject = subject;
        this.origin = origin;
        this.author = author;
        this.chartOpt = chartOpt;
        this.chartVersionOpt = chartVersionOpt;
        this.bases = new ArrayList<>();
        this.directions = new LinkedHashMap<>();
    }

    public Directions clone() {
        Directions result;

        result = new Directions(subject, origin, author, chartOpt, chartVersionOpt);
        result.bases.addAll(bases);
        result.directions.putAll(directions);
        return result;
    }

    public Directions with(String withOrigin, String withAuthor) {
        Directions result;

        result = new Directions(subject, withOrigin, withAuthor, chartOpt, chartVersionOpt);
        result.bases.addAll(bases);
        result.directions.putAll(directions);
        return result;
    }

    public void addNew(Direction direction) {
        if (directions.put(direction.name, direction) != null) {
            throw new IllegalStateException("direction already exists: " + direction.name);
        }
    }

    public List<Directions> createLayers(Toolkit toolkit) throws IOException {
        List<Directions> result;

        result = new ArrayList<>();
        addLayers(toolkit, result);
        return result;
    }

    private void addLayers(Toolkit toolkit, List<Directions> result) throws IOException {
        Directions layer;

        layer = clone();
        layer.bases.clear();
        result.add(layer);
        for (String base : bases) { // TODO: >1 bases ok?
            toolkit.directions(base).addLayers(toolkit, result);
        }
    }

    public void addMergedWithoutBases(Directions result) {
        for (Direction d : directions.values()) {
            result.addMerged(d);
        }
    }

    private void addMerged(Direction direction) {
        Direction prev;

        prev = directions.get(direction.name);
        if (prev != null) {
            if (prev.priv) {
                throw new IllegalStateException("you cannot override private direction: " + direction.name);
            }
            if (!prev.extra && direction.extra) {
                throw new IllegalStateException("missing extra modifier for extra direction: " + direction.name);
            }
            if (prev.doc != null && direction.doc == null) {
                direction = direction.withDoc(prev.doc);
            }
            if (!prev.expression.isEmpty() && direction.expression.isEmpty()) {
                direction = direction.withExpression(prev.expression);
            }
        }
        directions.put(direction.name, direction);
    }

    public void setValues(Map<String, String> values) {
        String key;
        String value;

        for (Map.Entry<String, String> entry : values.entrySet()) {
            key = entry.getKey();
            value = entry.getValue();
            if (value == null) {
                if (directions.remove(key) == null) {
                    throw new ArgumentException("direction not found: " + key);
                }
            } else {
                directions.put(key, new Direction(entry.getKey(), false, false, null, Direction.toExpression(entry.getValue())));
            }
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

    public List<String> toDescribe(Toolkit toolkit, String select) throws IOException {
        List<String> result;

        result = new ArrayList<>();
        toDescribe(toolkit, result, "", select);
        return result;
    }

    private void toDescribe(Toolkit toolkit, List<String> result, String space, String select) throws IOException {
        String moreSpace;

        result.add(space + DIRECTIONS + ": " + subject);
        moreSpace = space + "  ";
        for (String base : bases) {
            toolkit.directions(base).toDescribe(toolkit, result, moreSpace, select);
        }
        for (Direction d : directions.values()) {
            if (select == null || d.name.equals(select)) {
                d.toDescribe(result, moreSpace);
            }
        }
    }
}
