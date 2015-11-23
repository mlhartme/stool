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

    private static String toString(JsonObject obj) {
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
