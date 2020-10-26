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
package net.oneandone.stool.stockimage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


public class Context {
    public static Context fromYaml(JsonNode obj) {
        String token;

        if (obj.has("token")) {
            token = obj.get("token").asText();
        } else {
            token = null;
        }
        return new Context(obj.get("name").asText(), obj.get("url").asText(), token);
    }

    public final String name;
    /** url pointing to cubernetes cluster */
    public final String url;

    /** null to work anonymously */
    public String token;

    public Context(String name, String url, String token) {
        this.name = name;
        this.url = url;
        this.token = token;
    }

    public ObjectNode toYaml(ObjectMapper yaml) {
        ObjectNode result;

        result = yaml.createObjectNode();
        result.put("name", name);
        result.put("url", url);
        if (token != null) {
            result.put("token", token);
        }
        return result;
    }

    public int hashCode() {
        return name.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof Context) {
            return ((Context) obj).name.equals(name);
        } else {
            return false;
        }
    }
}
