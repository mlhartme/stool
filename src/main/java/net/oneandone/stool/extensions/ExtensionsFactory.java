package net.oneandone.stool.extensions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

public class ExtensionsFactory {
    public static ExtensionsFactory create(World world) {
        ExtensionsFactory factory;
        Properties properties;

        try {
            factory = new ExtensionsFactory();
            for (Node node : world.resources("META-INF/stool-extensions.properties")) {
                properties = node.readProperties();
                for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                    try {
                        factory.put((String) entry.getKey(), (Class) Class.forName((String) properties.get(entry.getValue())));
                    } catch (ClassNotFoundException e) {
                        throw new IllegalArgumentException("extension not found: " + entry.getValue());
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("unexpected io exception", e);
        }
        return factory;
    }

    //--

    private final Map<String, Class<? extends Extension>> types;

    public ExtensionsFactory() {
        types = new HashMap<>();
    }

    public void put(String name, Class<? extends Extension> extension) {
        if (types.put(name, extension) != null) {
            throw new IllegalArgumentException("duplicate extension: " + name);
        }
    }

    public Extensions newInstance() {
        Extensions extensions;

        extensions = new Extensions();
        for (Map.Entry<String, Class<? extends Extension>> entry : types.entrySet()) {
            try {
                extensions.add(entry.getKey(), entry.getValue().newInstance());
            } catch (InstantiationException | IllegalAccessException e) {
                throw new IllegalArgumentException("cannot instantiate extension " + entry.getValue().getName()
                        + ": " + e.getMessage(), e);
            }
        }
        return extensions;
    }

    public Extensions eatExtensions(JsonObject config) {
        Extensions extensions;
        String name;

        extensions = new Extensions();
        for (Map.Entry<String, Class<? extends Extension>> entry : types.entrySet()) {
            name = entry.getKey();
            extensions.add(name, eatExtension(name + ".", entry.getValue(), config));
        }
        return extensions;
    }

    private static Extension eatExtension(String prefix, Class<? extends Extension> clazz, JsonObject config) {
        JsonObject part;
        String name;
        Gson gson;
        Iterator<Map.Entry<String, JsonElement>> iter;
        Map.Entry<String, JsonElement> entry;

        part = new JsonObject();
        iter = config.entrySet().iterator();
        while (iter.hasNext()) {
            entry = iter.next();
            name = entry.getKey();
            if (name.startsWith(prefix)) {
                part.add(name.substring(prefix.length()), entry.getValue());
                iter.remove();
            }
        }
        gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.fromJson(part, clazz);
    }

}
