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
package net.oneandone.stool.classes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.oneandone.stool.core.Configuration;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.registry.TagInfo;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ClassRef {
    private enum Type {
        INLINE, IMAGE, BUILTIN
    }

    public static ClassRef parse(String str) {
        int idx;
        Type type;

        idx = str.indexOf('+');
        if (idx == -1) {
            throw new IllegalStateException(str);
        }
        type = Type.valueOf(str.substring(0, idx));
        str = str.substring(idx + 1);
        idx = str.indexOf('+');
        if (idx == -1) {
            throw new IllegalStateException(str);
        }
        return new ClassRef(type, decode(str.substring(0, idx)), decode(str.substring(idx + 1)));
    }


    private static String decode(String str) {
        return new String(Base64.getDecoder().decode(str), StandardCharsets.UTF_8);
    }

    private static String encode(String str) {
        return Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    public static ClassRef create(World world, String str) throws IOException {
        FileNode file;

        if (str.startsWith("/") || str.startsWith(".")) {
            file = world.file(str);
            file.checkFile();
            return new ClassRef(Type.INLINE, file.readString(), str);
        }
        if (str.contains("/")) {
            return new ClassRef(Type.IMAGE, str, str);
        } else {
            return new ClassRef(Type.BUILTIN, str, str);
        }
    }

    private final Type type;
    private final String value;
    private final String origin;

    public ClassRef(Type type, String value, String origin) {
        this.type = type;
        this.value = value;
        this.origin = origin;
    }

    public String serialize() {
        return type.toString() + "+" + encode(value) + "+" + encode(origin);
    }

    public static final String BUILDIN = "_buildin_";

    public Clazz resolve(String kubeContext, Configuration configuration) throws IOException {
        ObjectMapper yaml;
        Map<String, Clazz> all;
        Clazz result;
        String str;

        yaml = configuration.yaml;
        all = loadAll(yaml, configuration.resolvedCharts(kubeContext).values());
        switch (type) {
            case BUILTIN:
                result = all.get(value);
                if (result == null) {
                    throw new IOException("class not found: " + value);
                }
                break;
            case INLINE:
                try (Reader src = new StringReader(value)) {
                    try {
                        result = Clazz.loadLiteral(all, origin, null, object(yaml.readTree(src)));
                    } catch (IOException e) {
                        throw new IOException(origin + ": failed to parse class from file: " + e.getMessage(), e);
                    }
                }
                break;
            case IMAGE:
                Registry registry;
                TagInfo tag;

                registry = configuration.createRegistry(value);
                tag = registry.resolve(value);
                str = tag.labels.get("class");
                if (str == null || str.isEmpty()) {
                    throw new IOException("image does not have a 'class' label: " + value);
                }
                try (Reader src = new StringReader(decode(str))) {
                    try {
                        result = Clazz.loadLiteral(all, origin, tag.author, object(yaml.readTree(src)));
                    } catch (IOException e) {
                        throw new IOException(origin + ": failed to parse class from image label: " + e.getMessage(), e);
                    }
                }
                break;
            default:
                throw new IllegalStateException(type.toString());
        }
        return result;
    }

    private static ObjectNode object(JsonNode raw) throws IOException {
        if (raw instanceof ObjectNode) {
            return (ObjectNode) raw;
        } else {
            throw new IOException("object expected, got  " + raw.getNodeType());
        }
    }

    public static Map<String, Clazz> loadAll(ObjectMapper yaml, Collection<FileNode> charts) throws IOException {
        Iterator<JsonNode> classes;
        Map<String, Clazz> result;
        FileNode file;

        result = new HashMap<>();
        for (FileNode chart : charts) {
            add(result, Clazz.loadChartClass(yaml, chart.getName(), chart));
            file = chart.join("classes.yaml");
            if (file.exists()) {
                try (Reader src = file.newReader()) {
                    classes = yaml.readTree(src).elements();
                }
                while (classes.hasNext()) {
                    add(result, Clazz.loadLiteral(result, "builtin", BUILDIN, (ObjectNode) classes.next()));
                }
            }
        }
        return result;
    }

    private static void add(Map<String, Clazz> all, Clazz clazz) throws IOException {
        if (all.put(clazz.name, clazz) != null) {
            throw new IOException("duplicate class: " + clazz.name);
        }
    }
}
