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

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.oneandone.stool.util.Json;
import net.oneandone.stool.util.Pair;
import net.oneandone.sushi.util.Separator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.oneandone.stool.util.Json.string;

/** Immutable local settings */
public class LocalSettings {
    public final Map<String, Pair> registryCredentials;
    public final List<String> classpath;

    public static final Separator COLON = Separator.on(":").trim().skipEmpty();

    public LocalSettings(ObjectNode local) {
        this.registryCredentials = parseRegistryCredentials(string(local, "registryCredentials", ""));
        this.classpath = COLON.split(Json.string(local, "classpath", ""));
    }

    public static Map<String, Pair> parseRegistryCredentials(String str) {
        Map<String, Pair> result;
        int idx;
        String host;

        result = new HashMap<>();
        for (String entry : Separator.COMMA.split(str)) {
            idx = entry.indexOf('=');
            if (idx < 0) {
                throw new IllegalStateException(entry);
            }
            host = entry.substring(0, idx);
            entry = entry.substring(idx + 1);
            idx = entry.indexOf(':');
            if (idx < 0) {
                throw new IllegalStateException(entry);
            }
            result.put(host, new Pair(entry.substring(0, idx), entry.substring(idx + 1)));
        }
        return result;
    }

    private String registryCredentialsString() {
        StringBuilder result;

        result = new StringBuilder();
        for (Map.Entry<String, Pair> entry : registryCredentials.entrySet()) {
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(entry.getKey() + "=" + entry.getValue().left + ":" + entry.getValue().right);
        }
        return result.toString();
    }

    public void toYaml(ObjectNode local) {
        local.put("registryCredentials", registryCredentialsString());
        local.put("classpath", COLON.join(classpath));
    }

}
