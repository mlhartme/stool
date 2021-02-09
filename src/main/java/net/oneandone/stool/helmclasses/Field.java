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
package net.oneandone.stool.helmclasses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import net.oneandone.stool.util.Json;

/** Immutable building block of a class, defines how to compute values. */
public class Field {
    public static Field forYaml(String modeOpt, String name, JsonNode yaml) {
        boolean abstrct;
        boolean privt;
        boolean extra;
        String doc;
        String value;
        ObjectNode obj;

        privt = false;
        extra = false;
        doc = null;
        if (yaml.isObject()) {
            obj = (ObjectNode) yaml;
            privt = Json.bool(obj, "private", privt);
            extra = Json.bool(obj, "extra", extra);
            doc = Json.string(obj, "doc", null);
            value = getValue(obj, modeOpt);
        } else {
            value = yaml.asText();
        }
        return new Field(name, privt, extra, doc, value);
    }

    private static String getValue(ObjectNode obj, String modeOpt) {
        String result;

        result = modeOpt == null ? null : Json.string(obj, "value-" + modeOpt, null);
        return result != null ? result : Json.string(obj, "value", "");
    }

    public final String name;
    public final boolean privt;
    public final boolean extra;
    public final String doc;
    public final String value;

    public Field(String name, String value) {
        this(name, false, false, null, value);
    }
    public Field(String name, boolean privt, boolean extra, String doc, String value) {
        this.name = name;
        this.privt = privt;
        this.extra = extra;
        this.doc = doc;
        this.value = value;
    }

    public Field withValue(String withValue) {
        return new Field(name, privt, extra, doc, withValue);
    }

    public Field withDoc(String withDoc) {
        return new Field(name, privt, extra, withDoc, value);
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
            return new TextNode(value);
        } else {
            result.set("value", new TextNode(value));
            return result;
        }
    }
}
