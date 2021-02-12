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
package net.oneandone.stool.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.oneandone.sushi.fs.file.FileNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    public static ObjectMapper newYaml() {
        return new ObjectMapper(new YAMLFactory());
    }

    public static ObjectMapper newJson() {
        return new ObjectMapper();
    }

    //--

    public static List<String> list(ArrayNode json) {
        List<String> result;
        Iterator<JsonNode> iter;

        result = new ArrayList<>(json.size());
        iter = json.elements();
        while (iter.hasNext()) {
            result.add(iter.next().asText());
        }
        return result;
    }

    public static Map<String, JsonNode> map(ObjectNode infos) {
        Map<String, JsonNode> result;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;

        result = new LinkedHashMap<>();
        iter = infos.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public static Map<String, String> stringMapOpt(ObjectNode obj, String name) {
        ObjectNode entries;

        entries = (ObjectNode) obj.get(name);
        return entries == null ? new LinkedHashMap<>() : stringMap(obj);
    }

    public static Map<String, String> stringMap(ObjectNode obj) {
        Map<String, String> result;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;

        result = new LinkedHashMap<>();
        iter = obj.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            result.put(entry.getKey(), entry.getValue().asText());
        }
        return result;
    }

    public static Map<String, Pair> stringPairMap(ObjectNode obj) {
        Map<String, Pair> result;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;

        result = new LinkedHashMap<>();
        iter = obj.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            result.put(entry.getKey(), Pair.decode(entry.getValue().asText()));
        }
        return result;
    }

    public static int number(ObjectNode node, String field, int dflt) {
        return node.has(field) ? node.get(field).asInt() : dflt;
    }

    public static FileNode file(ObjectNode node, FileNode basedir, String field, FileNode dflt) {
        if (node.has(field)) {
            return basedir.getWorld().file(basedir, node.get(field).asText());
        } else {
            return dflt;
        }
    }

    public static boolean bool(ObjectNode node, String field, boolean dflt) {
        return node.has(field) ? node.get(field).asBoolean() : dflt;
    }

    public static String stringOpt(ObjectNode node, String field) {
        return string(node, field, null);
    }
    public static String string(ObjectNode node, String field, String dflt) {
        return node.has(field) ? node.get(field).asText() : dflt;
    }

    public static String string(ObjectNode obj, String field) {
        JsonNode element;

        element = obj.get(field);
        if (element == null) {
            throw new IllegalStateException(obj + ": field not found: " + field);
        }
        return element.asText();
    }

    public static Map<String, Object> toStringMap(ObjectNode obj, List<String> ignores) {
        Map<String, Object> result;
        String key;
        JsonNode value;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;
        Object v;

        result = new LinkedHashMap<>();
        iter = obj.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            key = entry.getKey();
            if (ignores.contains(key)) {
                continue;
            }
            value = entry.getValue();
            if (value.isNumber()) {
                v = value.asInt();
            } else if (value.isBoolean()) {
                v = value.asBoolean();
            } else if (value.isTextual()) {
                v = value.asText();
            } else {
                throw new IllegalStateException(value.toString());
            }
            result.put(key, v);
        }
        return result;
    }

    public static ObjectNode obj(ObjectMapper json, Map<String, String> obj) {
        ObjectNode result;

        result = json.createObjectNode();
        for (Map.Entry<String, String> entry : obj.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public static ObjectNode objPairs(ObjectMapper json, Map<String, Pair> obj) {
        ObjectNode result;

        result = json.createObjectNode();
        for (Map.Entry<String, Pair> entry : obj.entrySet()) {
            result.put(entry.getKey(), entry.getValue().encode());
        }
        return result;
    }

    public static JsonNode valueToJson(ObjectMapper json, Object value) {
        if (value == null) {
            return NullNode.getInstance();
        } else if (value instanceof List) {
            ArrayNode result;
            List<Object> lst;

            result = json.createArrayNode();
            lst = (List) value;
            for (Object item : lst) {
                result.add(valueToJson(json, item));
            }
            return result;
        } else if (value instanceof Map) {
            ObjectNode result;
            Map<Object, Object> map;

            result = json.createObjectNode();
            map = (Map) value;
            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                result.put((String) entry.getKey(), valueToJson(json, entry.getValue()));
            }
            return result;
        } else if (value instanceof Integer) {
            return new IntNode((Integer) value);
        } else {
            return new TextNode(value.toString());
        }
    }

    private Json() {
    }
}
