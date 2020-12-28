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
package net.oneandone.stool.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import net.oneandone.stool.kubernetes.Engine;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Field or Value */
public abstract class Info {
    private final String name;

    protected Info(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public abstract Object get(Engine engine) throws IOException;

    public String getAsString(Engine engine) throws IOException {
        return valueString(get(engine));

    }

    private static String valueString(Object value) {
        boolean first;
        List<Object> lst;
        StringBuilder builder;

        if (value == null) {
            return "";
        } else if (value instanceof List) {
            first = true;
            lst = (List) value;
            builder = new StringBuilder();
            for (Object item : lst) {
                if (first) {
                    first = false;
                } else {
                    builder.append('\t');
                }
                builder.append(valueString(item));
            }
            return builder.toString();
        } else {
            return value.toString();
        }
    }
    public JsonNode getAsJson(ObjectMapper json, Engine engine) throws IOException {
        return valueJson(json, get(engine));
    }

    private static JsonNode valueJson(ObjectMapper json, Object value) {
        if (value == null) {
            return NullNode.getInstance();
        } else if (value instanceof List) {
            ArrayNode result;
            List<Object> lst;

            result = json.createArrayNode();
            lst = (List) value;
            for (Object item : lst) {
                result.add(valueJson(json, item));
            }
            return result;
        } else if (value instanceof Map) {
            ObjectNode result;
            Map<Object, Object> map;

            result = json.createObjectNode();
            map = (Map) value;
            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                result.put((String) entry.getKey(), valueJson(json, entry.getValue()));
            }
            return result;
        } else if (value instanceof Integer) {
            return new IntNode((Integer) value);
        } else {
            return new TextNode(value.toString());
        }
    }
}
