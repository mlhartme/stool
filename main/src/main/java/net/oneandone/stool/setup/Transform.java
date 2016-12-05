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
package net.oneandone.stool.setup;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import net.oneandone.inline.ArgumentException;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

/** Transform json */
public class Transform {
    public static String transform(String srcString, Upgrade mapper) {
        JsonParser parser;
        JsonObject src;
        JsonObject dest;
        Object[] mapped;

        parser = new JsonParser();
        src = (JsonObject) parser.parse(srcString);
        dest = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : src.entrySet()) {
            mapped = map(mapper, entry.getKey(), entry.getValue());
            if (mapped != null) {
                dest.add((String) mapped[0], (JsonElement) mapped[1]);
            } else {
                // removed
            }
        }
        mapGlobal(mapper, src, dest);
        return toString(dest);
    }

    // TODO: needed to transform property names in defaults into camel case */
    private static String dotless(String name) {
        int idx;

        idx = name.indexOf('.');
        if (idx == -1) {
            return name;
        } else {
            return name.substring(0, idx) + Strings.capitalize(name.substring(idx + 1));
        }
    }

    private static void mapGlobal(Object mapper, JsonElement left, JsonElement right) {
        Method m;

        try {
            m = mapper.getClass().getDeclaredMethod("global", JsonObject.class, JsonObject.class);
        } catch (NoSuchMethodException e) {
            return;
        }
        try {
            m.invoke(mapper, left, right);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @return null to remove element; otherwise key/value pair (String/JsonObject)
     */
    private static Object[] map(Upgrade mapper, String name, JsonElement value) {
        Class clazz;
        Method rename;
        Method transform;
        String dotless;

        dotless = dotless(name);
        clazz = mapper.getClass();
        if (method(clazz, dotless + "Remove") != null) {
            return null;
        }
        rename = method(clazz, dotless + "Rename");
        transform = method(clazz, dotless + "Transform", JsonElement.class);
        return new Object[] { rename(rename, mapper, name), transform(transform, mapper, value) };
    }

    /* Search with method by name, check arguments later. Helps to detect methods with wrong arguments. */
    private static Method method(Class clazz, String name, Class ... args) {
        Method result;

        result = null;
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                if (result != null) {
                    throw new ArgumentException("method ambiguous: " + name);
                }
                result = m;
            }
        }
        if (result != null) {
            if (!Arrays.equals(args, result.getParameterTypes())) {
                throw new ArgumentException("argument type mismatch for method " + name);
            }
        }
        return result;
    }

    private static String rename(Method method, Object object, String old) {
        if (method == null) {
            return old;
        } else {
            try {
                return (String) method.invoke(object);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static JsonElement transform(Method method, Object object, JsonElement old) {
        if (method == null) {
            return old;
        } else {
            try {
                return (JsonElement) method.invoke(object, old);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public static String toString(JsonObject obj) {
        try {
            StringWriter stringWriter = new StringWriter();
            JsonWriter jsonWriter = new JsonWriter(stringWriter);
            jsonWriter.setIndent("  ");
            jsonWriter.setLenient(true);
            Streams.write(obj, jsonWriter);
            return stringWriter.toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
