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
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.oneandone.stool.util.Json;

/** Immutable */
public class Value {
    public static Value forYaml(String name, JsonNode yaml) {
        boolean abstrct;
        String value;
        ObjectNode obj;

        abstrct = false;
        if (yaml.isObject()) {
            obj = (ObjectNode) yaml;
            value = Json.string(obj, "value", "");
            abstrct = Json.bool(obj, "abstract", abstrct);
        } else {
            value = yaml.asText();
        }
        return new Value(name, abstrct, value);

    }
    public final String name;
    public final boolean abstrct;
    public final String macro;

    public Value(String name, boolean abstrct, String macro) {
        this.name = name;
        this.abstrct = abstrct;
        this.macro = macro;
    }
}
