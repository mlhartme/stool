package net.oneandone.stool.extensions;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.oneandone.stool.configuration.Until;
import net.oneandone.stool.configuration.adapter.UntilTypeAdapter;
import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.xml.XmlException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Extensions implements Iterable<Extension> {
    private final Map<String, Extension> extensions;

    public Extensions() {
        this.extensions = new HashMap<>();
    }

    public void add(String name, Extension extension) {
        if (extensions.put(name, extension) != null) {
            throw new IllegalArgumentException("duplicate extension: " + name);
        }
    }

    public Map<String, FileNode> vhosts(Stage stage) {
        Map<String, FileNode> result;

        result = new HashMap<>();
        for (Extension extension : extensions.values()) {
            result.putAll(extension.vhosts(stage));
        }
        return result;
    }

    public void beforeStart(Stage stage, Collection<String> apps) throws IOException {
        for (Extension extension : extensions.values()) {
            extension.beforeStart(stage, apps);
        }
    }

    public Map<String, String> contextParameter(Stage stage, String host, int httpPort, FileNode webinf) throws XmlException {
        Map<String, String> result;

        result = new HashMap<>();
        for (Extension extension : extensions.values()) {
            extension.contextParameter(stage, host, httpPort, webinf, result);
        }
        return result;
    }

    public void addAll(Extensions op) {
        extensions.putAll(op.extensions);
    }

    @Override
    public Iterator<Extension> iterator() {
        return extensions.values().iterator();
    }


    public void addConfig(JsonObject config) throws IOException {
        for (Map.Entry<String, Extension> entry : extensions.entrySet()) {
            addExtension(config, entry.getKey() + ".", entry.getValue());
        }
    }

    private void addExtension(JsonObject config, String prefix, Extension extension) {
        JsonObject part;

        part = (JsonObject) new GsonBuilder().setPrettyPrinting().create().toJsonTree(extension);
        for (Map.Entry<String, JsonElement> entry : part.entrySet()) {
            config.add(prefix + entry.getKey(), entry.getValue());
        }
    }
}
