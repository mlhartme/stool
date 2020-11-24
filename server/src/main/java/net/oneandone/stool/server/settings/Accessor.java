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
package net.oneandone.stool.server.settings;

import net.oneandone.stool.server.ArgumentException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Handles settings access. Converts between strings an objects and deals with reflection */
public class Accessor {
    private final String name;
    private final Field field;

    public Accessor(String name, Field field) {
        this.name = name;
        this.field = field;
        field.setAccessible(true);
    }

    public String get(Settings settings) {
        Object obj;
        StringBuilder builder;
        boolean first;

        try {
            obj = field.get(settings);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        if (obj instanceof List) {
            builder = new StringBuilder();
            first = true;
            for (Object item : (List) obj) {
                if (first) {
                    first = false;
                } else {
                    builder.append(", ");
                }
                builder.append(item.toString());
            }
            return builder.toString();
        } else if (obj instanceof Map) {
            builder = new StringBuilder();
            first = true;
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) obj).entrySet()) {
                if (first) {
                    first = false;
                } else {
                    builder.append(", ");
                }
                builder.append(entry.getKey().toString());
                builder.append(':');
                builder.append(entry.getValue().toString());
            }
            return builder.toString();
        } else if (obj == null) {
            return "null";
        } else {
            return obj.toString();
        }
    }

    public void set(Settings settings, String str) {
        Object value;
        Class type;
        int idx;
        Map<String, String> map;

        try {
            type = field.getType();
            if (type.equals(String.class)) {
                value = str;
            } else if (type.equals(Boolean.class) || type.equals(Boolean.TYPE)) {
                value = Boolean.valueOf(str);
            } else if (type.equals(Integer.class) || type.equals(Integer.TYPE)) {
                value = Integer.valueOf(str);
            } else if (Enum.class.isAssignableFrom(type)) {
                value = Enum.valueOf(type, str);
            } else if (type.equals(List.class)) {
                value = asList(str);
            } else if (type.equals(Map.class)) {
                map = new HashMap<>();
                for (String item : asList(str)) {
                    idx = item.indexOf(':');
                    if (idx == -1) {
                        throw new ArgumentException("cannot set property '" + name + "': expected key:value, got " + item);
                    }
                    map.put(item.substring(0, idx).trim(), item.substring(idx + 1).trim());
                }
                value = map;
            } else if (type.equals(Expire.class)) {
                value = Expire.fromHuman(str);
            } else if (type.equals(FileNode.class)) {
                value = str.isEmpty() ? null : World.createMinimal().file(str);
            } else if (Map.class.isAssignableFrom(type)) {
                value = str;
            } else {
                throw new IllegalStateException(name + ": cannot convert String to " + type.getSimpleName());
            }
        } catch (RuntimeException e) {
            throw new ArgumentException(field.getName() + ": invalid value: '" + str + "': " + e.getMessage());
        }
        try {
            field.set(settings, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public int hashCode() {
        return name.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof Accessor) {
            return name.equals(((Accessor) obj).name);
        }
        return false;
    }

    private static List<String> asList(String str) {
        List<String> result;

        if (str.contains(",")) {
            result = Arrays.asList(str.split(","));
        } else if (str.length() > 0) {
            result = new ArrayList<>();
            result.add(str);
        } else {
            result = Collections.emptyList();
        }
        return result;
    }
}
