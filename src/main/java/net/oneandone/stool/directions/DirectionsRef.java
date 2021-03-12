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
package net.oneandone.stool.directions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.oneandone.stool.core.LocalSettings;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.registry.TagInfo;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class DirectionsRef {
    private enum Type {
        INLINE, IMAGE, BUILTIN
    }

    public static DirectionsRef parse(String str) {
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
        return new DirectionsRef(type, decode(str.substring(0, idx)), decode(str.substring(idx + 1)));
    }


    private static String decode(String str) {
        return new String(Base64.getDecoder().decode(str), StandardCharsets.UTF_8);
    }

    private static String encode(String str) {
        return Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    public static DirectionsRef create(World world, String str) throws IOException {
        FileNode file;

        if (str.startsWith("/") || str.startsWith(".")) {
            file = world.file(str);
            file.checkFile();
            return new DirectionsRef(Type.INLINE, file.readString(), str);
        }
        if (str.contains("/")) {
            return new DirectionsRef(Type.IMAGE, str, str);
        } else {
            return new DirectionsRef(Type.BUILTIN, str, str);
        }
    }

    private final Type type;
    private final String value;
    private final String origin;

    public DirectionsRef(Type type, String value, String origin) {
        this.type = type;
        this.value = value;
        this.origin = origin;
    }

    public String serialize() {
        return type.toString() + "+" + encode(value) + "+" + encode(origin);
    }

    public static final String BUILDIN = "_buildin_";

    public static final String LABEL = "directions";

    public Directions resolve(LocalSettings localSettings) throws IOException {
        Toolkit toolkit;
        Directions result;
        String str;

        toolkit = localSettings.toolkit();
        switch (type) {
            case BUILTIN:
                result = toolkit.directions(value);
                break;
            case INLINE:
                try (Reader src = new StringReader(value)) {
                    try {
                        result = Directions.loadLiteral(origin, null, object(localSettings.yaml.readTree(src)));
                    } catch (IOException e) {
                        throw new IOException(origin + ": failed to parse directions from file: " + e.getMessage(), e);
                    }
                }
                break;
            case IMAGE:
                Registry registry;
                TagInfo tag;

                registry = localSettings.createRegistry(value);
                tag = registry.resolve(value);
                str = tag.labels.get(LABEL);
                if (str == null || str.isEmpty()) {
                    throw new IOException("image does not have a '" + LABEL + "' label: " + value);
                }
                try (Reader src = new StringReader(decode(str))) {
                    try {
                        result = Directions.loadLiteral(origin, tag.author, object(localSettings.yaml.readTree(src)));
                    } catch (IOException e) {
                        throw new IOException(origin + ": failed to parse directions from image label: " + e.getMessage(), e);
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
}
