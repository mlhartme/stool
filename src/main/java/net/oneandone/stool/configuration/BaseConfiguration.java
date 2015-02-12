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

import net.oneandone.stool.util.Ports;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BaseConfiguration {
    public void configure(String key, Object strOrMap) throws NoSuchFieldException {
        Field field;
        Class type;
        Object value;
        String str;

        field = getField(key) != null ? getField(key) : getFieldByAnnotation(key);
        if (field == null) {
            throw new NoSuchFieldException(key + " not found");
        }
        field.setAccessible(true);
        type = field.getType();
        if (type.equals(String.class)) {
            value = strOrMap;
        } else if (type.equals(Boolean.class)) {
            value = Boolean.valueOf((String) strOrMap);
        } else if (type.equals(Integer.class) || type.equals(Integer.TYPE)) {
            value = Integer.valueOf((String) strOrMap);
        } else if (type.equals(List.class)) {
            str = (String) strOrMap;
            if (str.contains(",")) {
                value = Arrays.asList(str.split(","));
            } else if (str.contains(" ")) {
                value = Arrays.asList(str.split(" "));
            } else if (str.length() > 0) {
                ArrayList<String> list = new ArrayList<>();
                list.add(str);
                value = list;
            } else {
                value = Collections.emptyList();
            }
        } else if (type.equals(Until.class)) {
            value = Until.fromHuman((String) strOrMap);
        } else if (type.equals(Ports.PortData.class)) {
            value = new Ports.PortData(Integer.parseInt((String) strOrMap));
        } else if (Map.class.isAssignableFrom(type)) {
            value = strOrMap;
        } else {
            throw new IllegalStateException("Cannot convert String to " + type.getSimpleName());
        }
        try {
            field.set(this, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private Field getField(String key) {
        try {
            return this.getClass().getField(key);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    public Field getFieldByAnnotation(String key) {
        for (Field field : this.getClass().getFields()) {
            if (field.isAnnotationPresent(Option.class) && field.getAnnotation(Option.class).key().equals(key)) {
                return field;
            }
        }
        return null;
    }

}
