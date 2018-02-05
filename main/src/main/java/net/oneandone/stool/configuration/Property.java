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
package net.oneandone.stool.configuration;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.templates.Switch;
import net.oneandone.stool.util.Info;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Handles Stool or Stage property. Converts between strings an objects and deals with reflection */
public class Property implements Info {
    public final String name;
    private final Field field;
    private final String template;

    public Property(String name, Field field, String template) {
        this.name = name;
        this.field = field;
        this.template = template;
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
                    throw new ArgumentException("cannot set property '" + name + "': expected key:value, got " + item);
                }
                map.put(item.substring(0, idx).trim(), item.substring(idx + 1).trim());
            }
            value = map;
        } else if (type.equals(Expire.class)) {
            value = Expire.fromHuman(str);
        } else if (Map.class.isAssignableFrom(type)) {
            value = str;
        } else {
            throw new IllegalStateException(name + ": cannot convert String to " + type.getSimpleName());
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

        if (template == null) {
            return configuration;
        } else {
            s = ((StageConfiguration) configuration).templates.get(template);
            if (s == null) {
                throw new IllegalStateException("missing template: " + template);
            }
            if (name.equals(template)) {
                return s;
            } else {
                return s.extension;
            }
        }
    }

    public int hashCode() {
        return name.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof Property) {
            return name.equals(((Property) obj).name);
        }
        return false;
    }

    @Override
    public String infoName() {
        return name;
    }
}
