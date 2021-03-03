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
import net.oneandone.stool.classes.Helm;
import net.oneandone.stool.registry.PortusRegistry;
import net.oneandone.stool.util.Json;
import net.oneandone.stool.util.Pair;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.oneandone.stool.util.Json.string;

/** Immutable local settings */
public class LocalSettings {
    public final ObjectMapper json;

    public final Map<String, Pair> registryCredentials;
    public final List<String> classpath;
    public final FileNode lib;

    public static final Separator COLON = Separator.on(":").trim().skipEmpty();

    public LocalSettings(ObjectMapper json, FileNode home, ObjectNode local) {
        this.json = json;
        this.registryCredentials = parseRegistryCredentials(string(local, "registryCredentials", ""));
        this.classpath = COLON.split(Json.string(local, "classpath", ""));
        this.lib = home.join("lib");
    }

    public LocalSettings(World world, ObjectMapper json, LocalSettings from) {
        this.json = json;
        this.registryCredentials = new HashMap<>(from.registryCredentials);
        this.classpath = new ArrayList<>(from.classpath);
        this.lib = world.file(from.lib.toPath().toFile());

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

    public PortusRegistry createRegistry(String image) throws IOException {
        int idx;
        String host;
        Pair up;
        String uri;

        idx = image.indexOf('/');
        if (idx == -1) {
            throw new IllegalArgumentException(image);
        }
        host = image.substring(0, idx);
        uri = "https://";
        up = registryCredentials(host);
        if (up != null) {
            uri = uri + up.left + ":" + up.right + "@";
        }
        uri = uri + host;
        return PortusRegistry.create(json, lib.getWorld(), uri, null);
    }

    public Pair registryCredentials(String registry) {
        return registryCredentials.get(registry);
    }



    public Map<String, FileNode> resolvedCharts(String kubeContext) throws IOException {
        FileNode root;
        PortusRegistry portus;
        Map<String, FileNode> result;
        FileNode resolved;

        root = lib.join("charts").mkdirsOpt();
        result = new LinkedHashMap<>();
        for (String entry : classpath) {
            resolved = directoryChartOpt(entry);
            if (resolved == null) {
                portus = createRegistry(entry);
                resolved = Helm.resolveRepositoryChart(kubeContext, portus, entry, root).checkDirectory();
            }
            result.put(resolved.getName(), resolved);
        }
        return result;
    }

    private FileNode directoryChartOpt(String classpathEntry) throws IOException {
        if (classpathEntry.startsWith("/")) {
            return lib.getWorld().file(classpathEntry).checkDirectory();
        } else {
            return null;
        }
    }
}
