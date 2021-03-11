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
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.oneandone.inline.ArgumentException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RawDirections {
    public static RawDirections load(ObjectNode directions) throws IOException {
        Map<String, JsonNode> raw;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;

        raw = new LinkedHashMap<>();
        iter = directions.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            raw.put(entry.getKey(), entry.getValue());
        }
        return new RawDirections(eatString(raw, Directions.DIRECTIONS),
                eatStringOpt(raw, Directions.ORIGIN), eatStringOpt(raw, Directions.AUTHOR),
                eatStringOpt(raw, Directions.CHART), eatStringOpt(raw, Directions.CHART_VERSION),
                stringListOpt(raw.remove(Directions.EXTENDS)), raw);
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

    public final String subject;
    public final String origin;
    public final String author;
    public final String chart;
    public final String chartVersion;
    public final List<String> bases;
    public final Map<String, JsonNode> directions;

    public RawDirections(String subject, String origin, String author, String chart, String chartVersion,
                         List<String> bases, Map<String, JsonNode> directions) {
        this.subject = subject;
        this.origin = origin;
        this.author = author;
        this.chart = chart;
        this.chartVersion = chartVersion;
        this.bases = bases;
        this.directions = directions;
    }
}
