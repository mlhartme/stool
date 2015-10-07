package net.oneandone.stool.setup;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import net.oneandone.sushi.cli.ArgumentException;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

/** Transform json */
public class Transform {
    public static String mergeConfig(String srcString, String destString, Object mapper) {
        JsonParser parser;
        JsonObject src;
        JsonObject dest;
        JsonObject target;
        Object[] mapped;
        String name;
        int idx;
        String extension;

        parser = new JsonParser();
        src = (JsonObject) parser.parse(srcString);
        dest = (JsonObject) parser.parse(destString);
        for (Map.Entry<String, JsonElement> entry : src.entrySet()) {
            mapped = map(mapper, entry.getKey(), entry.getValue());
            if (mapped != null) {
                name = (String) mapped[0];
                idx = name.indexOf('+');
                if (idx != -1) {
                    extension = name.substring(0, idx);
                    name = name.substring(idx + 1);
                    target = dest.getAsJsonObject().get("extensions").getAsJsonObject();
                    target = target.get((target.has("+" + extension) ? "+" : "-") + extension).getAsJsonObject();
                } else {
                    target = dest;

                }
                target.add(name, (JsonElement) mapped[1]);
            }
        }
        mapGlobal(mapper, src, dest);
        return toString(dest);
    }

    private static void mapGlobal(Object mapper, JsonElement left, JsonElement right) {
        Method m;

        try {
            m = mapper.getClass().getDeclaredMethod("global", JsonElement.class, JsonElement.class);
        } catch (NoSuchMethodException e) {
            return;
        }
        try {
            m.invoke(mapper, left, right);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object[] map(Object mapper, String name, JsonElement value) {
        Class clazz;
        Method rename;
        Method transform;

        clazz = mapper.getClass();
        if (method(clazz, name + "Remove") != null) {
            return null;
        }
        rename = method(clazz, name + "Rename");
        transform = method(clazz, name + "Transform", JsonElement.class);
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
