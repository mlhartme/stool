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
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.util.Json;

import java.io.IOException;

/** A computable value representing one aspect of the stage status. */
public abstract class Property {
    public final boolean hidden;
    private final String name;

    protected Property(String name) {
        this(name, false);
    }

    protected Property(String name, boolean hidden) {
        this.name = name;
        this.hidden = hidden;
    }

    public String toString() {
        return name();
    }

    public String name() {
        return name;
    }

    public abstract Object get(Engine engine) throws IOException;

    public JsonNode getAsJson(ObjectMapper json, Engine engine) throws IOException {
        return Json.valueToJson(json, get(engine));
    }
}