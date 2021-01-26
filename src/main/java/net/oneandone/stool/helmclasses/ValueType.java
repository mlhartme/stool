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

/** Immutable */
public class ValueType {
    public static ValueType forYaml(String name, JsonNode yaml) {
        boolean abstrct;
        boolean privt;
        String doc;
        String value;
        ObjectNode obj;

        abstrct = false;
        privt = false;
        doc = null;
        if (yaml.isObject()) {
            obj = (ObjectNode) yaml;
            abstrct = Json.bool(obj, "abstract", abstrct);
            privt = Json.bool(obj, "private", privt);
            doc = Json.string(obj, "doc", null);
            value = Json.string(obj, "value", "");
        } else {
            value = yaml.asText();
        }
        return new ValueType(name, abstrct, privt, doc, value);

    }
    public final String name;
    public final boolean abstrct;
    public final boolean privt;
    public final String doc;
    public final String value;

    public ValueType(String name, boolean abstrct, boolean privt, String doc, String value) {
        this.name = name;
        this.abstrct = abstrct;
        this.privt = privt;
        this.doc = doc;
        this.value = value;
    }

    public ValueType withValue(String withValue) {
        return new ValueType(name, abstrct, privt, doc, withValue);
    }

    public ValueType withDoc(String withDoc) {
        return new ValueType(name, abstrct, privt, withDoc, value);
    }

    public JsonNode toObject(ObjectMapper yaml) {
        ObjectNode result;

        result = yaml.createObjectNode();
        if (abstrct) {
            result.set("abstract", BooleanNode.valueOf(abstrct));
        }
        if (privt) {
            result.set("private", BooleanNode.valueOf(privt));
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
