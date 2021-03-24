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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.oneandone.stool.directions.Directions;
import net.oneandone.stool.directions.Variable;

import java.util.Map;

/** Mostly a name for an expression, can be evaluated. Immutable. */
public class Sequence {
    public final Directions merged;
    private final Directions config;

    public Sequence(Directions merged, Directions config) {
        this.merged = merged;
        this.config = config;
    }

    public String chartString() {
        return merged.chartOpt + ":" + merged.chartVersionOpt;
    }

    public boolean fixed(String name) {
        return config.directions.containsKey(name);
    }

    public String value(Variable variable) {
        String result;

        result = variable.get();
        if (!fixed(variable.name)) {
            result = result + " # " + merged.get(variable.name).expression;
        }
        return result;
    }

    public ObjectNode toObject(ObjectMapper yaml) {
        ObjectNode result;

        result = yaml.createObjectNode();
        result.set("instance", merged.toObject(yaml));
        result.set("config", config.toObject(yaml));
        return result;
    }

    public Directions nextConfig(Map<String, String> overrides) {
        Directions result;

        result = config.clone();
        result.setValues(overrides);
        return result;
    }
}
