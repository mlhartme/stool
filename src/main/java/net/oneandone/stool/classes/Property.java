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
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.util.Json;
import net.oneandone.sushi.util.Strings;

import java.util.Map;

/** Immutable building block of a class, defines how to compute values. */
public class Property {
    public static Property forYaml(String name, JsonNode yaml) {
        boolean privt;
        boolean extra;
        String doc;
        String function;
        ObjectNode obj;

        privt = false;
        extra = false;
        doc = null;
        if (yaml.isObject()) {
            obj = (ObjectNode) yaml;
            privt = Json.bool(obj, "private", privt);
            extra = Json.bool(obj, "extra", extra);
            doc = Json.string(obj, "doc", null);
            function = getFunction(obj);
        } else {
            function = yaml.asText();
        }
        return new Property(name, privt, extra, doc, function);
    }

    public static String getFunction(ObjectNode root) {
        JsonNode v;
        ObjectNode obj;
        String var;
        String dflt;
        StringBuilder result;

        v = root.get("function");
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
            throw new ArgumentException("malformed function: " + v.toString());
        }
    }

    private static void str(StringBuilder builder, String str) {
        builder.append('\'').append(str.replace("'", "\\'")).append('\'');
    }

    public final String name;
    public final boolean privt;
    public final boolean extra;
    public final String doc;
    public final String function;

    public Property(String name, String function) {
        this(name, false, false, null, function);
    }
    public Property(String name, boolean privt, boolean extra, String doc, String function) {
        this.name = name;
        this.privt = privt;
        this.extra = extra;
        this.doc = doc;
        this.function = function;
    }

    public Property withFunction(String withFunction) {
        return new Property(name, privt, extra, doc, withFunction);
    }

    public Property withDoc(String withDoc) {
        return new Property(name, privt, extra, withDoc, function);
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
        if (result.isEmpty()) {
            return new TextNode(function);
        } else {
            result.set("function", new TextNode(function));
            return result;
        }
    }
}
