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
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.directions.Direction;
import net.oneandone.stool.directions.Directions;
import net.oneandone.stool.directions.Toolkit;
import net.oneandone.stool.directions.Variable;
import net.oneandone.stool.util.Diff;
import net.oneandone.stool.util.Expire;
import net.oneandone.stool.util.Json;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.Map;

/** Mostly a name for an expression, can be evaluated. Immutable. */
public class Sequence {
    public final Directions merged;
    private final Directions config;

    public Sequence(Directions merged, Directions config) {
        this.merged = merged;
        this.config = config;
    }

    public String subject() {
        return merged.subject;
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

    public Directions configMerged(Toolkit toolkit) throws IOException {
        Directions result;

        result = merged.clone();
        config.addMerged(toolkit, result);
        return result;
    }

    public Object origin() {
        return merged.origin;
    }


    public FileNode createValuesFile(ObjectMapper yaml, World world, Map<String, String> actuals) throws IOException {
        ObjectNode dest;
        Expire expire;
        FileNode file;
        String str;

        dest = yaml.createObjectNode();
        for (Map.Entry<String, String> entry : actuals.entrySet()) {
            dest.put(entry.getKey(), entry.getValue());
        }

        dest.set(Directions.MERGED_INSTANCE_DIRECTIONS_VALUE, merged.toObject(yaml));
        dest.set(Directions.CONFIG_DIRECTIONS_VALUE, config.toObject(yaml));

        // check expire - TODO: ugly up reference to core package
        str = Json.string(dest, Dependencies.VALUE_EXPIRE, null);
        if (str != null) {
            expire = Expire.fromString(str);
            if (expire.isExpired()) {
                throw new ArgumentException("stage expired: " + expire);
            }
            dest.put(Dependencies.VALUE_EXPIRE, expire.toString());
        }

        file = world.getTemp().createTempFile().writeString(dest.toPrettyString());
        return file;
    }

    public void removePrivate(Diff result) {
        for (Direction direction : merged.directions.values()) {
            if (direction.priv) {
                result.remove(direction.name);
            }
        }
    }
}
