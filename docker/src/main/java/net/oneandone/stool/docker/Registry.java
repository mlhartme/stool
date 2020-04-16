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
package net.oneandone.stool.docker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.oneandone.sushi.fs.http.HttpNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * See https://docs.docker.com/registry/spec/api/
 * and https://docs.docker.com/registry/deploying/
 */
public class Registry {
    private final HttpNode root;

    /** Thread safe - has no fields at all */
    private final JsonParser parser;

    public Registry(HttpNode root) {
        this.root = root;
        this.parser = new JsonParser();
    }

    public List<String> catalog() throws IOException {
        JsonObject result;

        result = JsonParser.parseString(root.join("v2/_catalog").readString()).getAsJsonObject();
        return toList(result.get("repositories").getAsJsonArray());
    }

    private static List<String> toList(JsonArray array) {
        List<String> result;

        result = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            result.add(element.getAsString());
        }
        return result;
    }

}
