/**
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
package net.oneandone.stool.configuration;

import net.oneandone.stool.extensions.Switch;
import net.oneandone.sushi.cli.ArgumentException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Handles Stool or Stage property. Converts between strings an objects and deals with reflection */
public class Property {
    public final String name;
    private final Field field;
    private final String extension;

    public Property(String name, Field field, String extension) {
        this.name = name;
        this.field = field;
        this.extension = extension;
        field.setAccessible(true);
    }

    public String get(StoolConfiguration configuration) {
        return doGet(configuration);
    }
    public String get(StageConfiguration configuration) {
        return doGet(configuration);
    }

    private String doGet(Object configuration) {
        Object obj;
        StringBuilder builder;
        boolean first;

        try {
            obj = field.get(object(configuration));
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
        } else {
            return obj.toString();
        }
    }

    // TODO: change strOrMap to str when it's no longer used for stool.defaults
    public void set(Object configuration, String str) {
        Object value;
        Class type;
        int idx;
        Map<String, String> map;

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
                    throw new ArgumentException("expected key:value, got " + item);
                }
                map.put(item.substring(0, idx).trim(), item.substring(idx + 1).trim());
            }
            value = map;
        } else if (type.equals(Until.class)) {
            value = Until.fromHuman(str);
        } else if (Map.class.isAssignableFrom(type)) {
            value = str;
        } else {
            throw new IllegalStateException("Cannot convert String to " + type.getSimpleName());
        }
        try {
            field.set(object(configuration), value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> asList(String str) {
        List<String> result;

        if (str.contains(",")) {
            result = Arrays.asList(str.split(","));
        } else if (str.length() > 0) {
            ArrayList<String> list = new ArrayList<>();
            list.add(str);
            result = list;
        } else {
            result = Collections.emptyList();
        }
        return result;
    }

    private Object object(Object configuration) {
        Switch s;

        if (extension == null) {
            return configuration;
        } else {
            s = ((StageConfiguration) configuration).extensions.get(extension);
            if (s == null) {
                throw new IllegalStateException("missing extension: " + extension);
            }
            if (name.equals(extension)) {
                return s;
            } else {
                return s.extension;
            }
        }
    }
}
