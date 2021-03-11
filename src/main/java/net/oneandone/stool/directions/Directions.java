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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Directions {
    // this value is added to track the stage directions used
    public static final String DIRECTIONS_VALUE = "_directions";

    // TODO: move to core package
    public static Directions loadStageDirectionsBase(World world, ObjectMapper yaml, Toolkit toolkit) throws IOException {
        try (Reader src = world.resource("stage.yaml").newReader()) {
            return Directions.loadLiteral(toolkit, "root", "stool", (ObjectNode) yaml.readTree(src));
        }
    }

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

    /** from inline, label or file; always extends */
    public static Directions loadLiteral(Toolkit toolkit, String origin, String author, ObjectNode directions) throws IOException {
        return loadLiteral(toolkit, origin, author, RawDirections.load(directions));
    }
    /** from inline, label or file; always extends */
    public static Directions loadLiteral(Toolkit toolkit, String origin, String author, RawDirections raw) throws IOException {
        Directions derived;
        List<Directions> bases;

        bases = new ArrayList();
        for (String baseName : raw.bases) {
            bases.add(toolkit.directions(baseName));
        }
        derived = extend(origin, author, raw.subject, bases);
        for (Map.Entry<String, JsonNode> entry : raw.directions.entrySet()) {
            derived.addMerged(Direction.forYaml(entry.getKey(), entry.getValue()));
        }
        return derived;
    }

    public static Directions loadHelm(ObjectNode directions) throws IOException {
        RawDirections raw;
        Directions result;

        raw = RawDirections.load(directions);
        // chart + version are mandatory here because a stage was created with them:
        if (raw.chart == null || raw.chartVersion == null) {
            throw new IllegalStateException();
        }
        result = new Directions(raw.subject, raw.origin, raw.author, raw.chart, raw.chartVersion);
        for (Map.Entry<String, JsonNode> entry : raw.directions.entrySet()) {
            result.addNew(Direction.forYaml(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public static Directions forTest(String name, String... nameValues) {
        Directions result;

        result = new Directions(name, "synthetic", null, "unusedChart", "noVersion");
        for (int i = 0; i < nameValues.length; i += 2) {
            result.addNew(new Direction(nameValues[i], nameValues[i + 1]));
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

    public void addNew(Direction direction) {
        if (directions.put(direction.name, direction) != null) {
            throw new IllegalStateException("direction already exists: " + direction.name);
        }
    }

    public void addMerged(Direction direction) {
        Direction prev;

        prev = directions.get(direction.name);
        if (prev != null) {
            if (prev.privt) {
                throw new IllegalStateException("you cannot override private direction: " + direction.name);
            }
            if (prev.extra || direction.extra) {
                throw new IllegalStateException("extra direction is not unique: " + direction.name);
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
                result.addMerged(direction);
            }
        }
        return result;
    }
}
