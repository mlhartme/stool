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
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.util.Json;
import net.oneandone.sushi.util.Strings;

import java.util.List;
import java.util.Map;

/** Mostly a name for an expression, can be evaluated. Immutable. */
public class Direction {
    private static final String VALUE_PREFIX = "=";
    private static final int VALUE_PREFIX_LENGTH = VALUE_PREFIX.length();

    public static String toExpression(String value) {
        return value.isEmpty() ? value : VALUE_PREFIX + value;
    }

    public static Direction forYaml(String name, JsonNode yaml) {
        boolean privt;
        boolean extra;
        String doc;
        String expression;
        ObjectNode obj;

        privt = false;
        extra = false;
        doc = null;
        if (yaml.isObject()) {
            obj = (ObjectNode) yaml;
            privt = Json.bool(obj, "private", privt);
            extra = Json.bool(obj, "extra", extra);
            doc = Json.string(obj, "doc", null);
            expression = getExpression(obj);
        } else {
            expression = toExpression(yaml.asText());
        }
        return new Direction(name, privt, extra, doc, expression);
    }

    public static String getExpression(ObjectNode root) {
        JsonNode v;
        ObjectNode obj;
        String var;
        String dflt;
        StringBuilder result;

        v = root.get("expr");
        if (v == null) {
            return "";
        } else if (v.isTextual() || v.isNumber()) {
            return v.asText();
        } else if (v.isObject()) {
            obj = (ObjectNode) v;
            var = Json.string(obj, "var", "MODE");
            dflt = Json.string(obj, "default");
            result = new StringBuilder();
            result.append("${ switch(");
            str(result, var);
            result.append(',');
            str(result, dflt);
            for (Map.Entry<String, Object> entry : Json.toStringMap(obj, Strings.toList("default", "var")).entrySet()) {
                result.append(',');
                str(result, entry.getKey());
                result.append(',');
                str(result, entry.getValue().toString());
            }
            result.append(") }");
            return result.toString();
        } else {
            throw new ArgumentException("malformed expression: " + v.toString());
        }
    }

    private static void str(StringBuilder builder, String str) {
        builder.append('\'').append(str.replace("'", "\\'")).append('\'');
    }

    public final String name;
    public final boolean privt;
    public final boolean extra;
    public final String doc;

    /** =(value) or (freemarker expression) */
    public final String expression;

    public Direction(String name, String expression) {
        this(name, false, false, null, expression);
    }
    public Direction(String name, boolean privt, boolean extra, String doc, String expression) {
        this.name = name;
        this.privt = privt;
        this.extra = extra;
        this.doc = doc;
        this.expression = expression;
    }

    public Direction withExpression(String withExpression) {
        return new Direction(name, privt, extra, doc, withExpression);
    }

    public Direction withDoc(String withDoc) {
        return new Direction(name, privt, extra, withDoc, expression);
    }

    public JsonNode toObject(ObjectMapper yaml) {
        ObjectNode result;

        result = yaml.createObjectNode();
        if (privt) {
            result.set("private", BooleanNode.valueOf(privt));
        }
        if (extra) {
            result.set("extra", BooleanNode.valueOf(extra));
        }
        if (doc != null) {
            result.set("doc", new TextNode(doc));
        }
        if (result.isEmpty() && isValue()) {
            return new TextNode(valueOpt());
        } else {
            result.set("expr", new TextNode(expression));
            return result;
        }
    }

    public void toDescribe(List<String> result, String space) {
        result.add(space + name + ": " + expression);
    }

    public boolean isValue() {
        return expression.isEmpty() || expression.startsWith(VALUE_PREFIX);
    }

    public String valueOpt() {
        if (expression.isEmpty()) {
            return expression;
        } else {
            return expression.startsWith(VALUE_PREFIX) ? expression.substring(VALUE_PREFIX_LENGTH) : null;
        }
    }
}
