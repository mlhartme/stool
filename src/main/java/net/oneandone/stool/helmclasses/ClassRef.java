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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ClassRef {
    private enum Type {
        INLINE, IMAGE, BUILTIN
    }

    public static ClassRef parse(String str) {
        int idx;

        idx = str.indexOf('+');
        if (idx == -1) {
            throw new IllegalStateException(str);
        }
        return new ClassRef(Type.valueOf(str.substring(0, idx)), decode(str.substring(idx + 1)));
    }


    private static String decode(String str) {
        return new String(Base64.getDecoder().decode(str), StandardCharsets.UTF_8);
    }

    private static String encode(String str) {
        return Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    public static ClassRef create(World world, String str) throws IOException {
        FileNode file;

        file = world.file(str);
        if (file.exists()) {
            return new ClassRef(Type.INLINE, file.readString());
        }
        if (str.contains("/")) {
            return new ClassRef(Type.IMAGE, str);
        } else {
            return new ClassRef(Type.BUILTIN, str);
        }
    }

    private final Type type;
    private final String value;

    public ClassRef(Type type, String value) {
        this.type = type;
        this.value = value;
    }

    public String serialize() {
        return type.toString() + "+" + encode(value);
    }

    public static final String BUILDIN = "_buildin_";

    public Clazz resolve(Configuration configuration, World world, ObjectMapper yaml, FileNode root) throws IOException {
        Map<String, Clazz> all;
        Clazz result;
        String str;

        all = loadAll(yaml, root);
        switch (type) {
            case BUILTIN:
                result = loadAll(yaml, root).get(value);
                if (result == null) {
                    throw new IOException("class not found: " + value);
                }
                break;
            case INLINE:
                try (Reader src = new StringReader(value)) {
                    result = Clazz.load(yaml, all, null, (ObjectNode) yaml.readTree(src), root);
                }
                break;
            case IMAGE:
                Registry registry;
                TagInfo tag;

                registry = configuration.createRegistry(world, value);
                tag = registry.resolve(value);
                str = tag.labels.get("class");
                if (str == null) {
                    throw new IOException("image does not have a class label: " + value);
                }
                try (Reader src = new StringReader(decode(str))) {
                    result = Clazz.load(yaml, all, tag.author, (ObjectNode) yaml.readTree(src), root);
                }
                break;
            default:
                throw new IllegalStateException(type.toString());
        }
        return result;
    }

    public static Map<String, Clazz> loadAll(ObjectMapper yaml, FileNode root) throws IOException {
        Iterator<JsonNode> classes;
        Map<String, Clazz> result;
        Clazz clazz;

        try (Reader src = root.join("classes.yaml").newReader()) {
            classes = yaml.readTree(src).elements();
        }
        result = new HashMap<>();
        while (classes.hasNext()) {
            clazz = Clazz.load(yaml, result, BUILDIN, (ObjectNode) classes.next(), root);
            if (result.put(clazz.name, clazz) != null) {
                throw new IOException("duplicate class: " + clazz.name);
            }
        }
        return result;
    }

}