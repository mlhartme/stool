package net.oneandone.stool.extensions;

import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.xml.XmlException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Extensions implements Iterable<Extension> {
    public final Map<String, Extension> extensions;

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

    @Override
    public Iterator<Extension> iterator() {
        return extensions.values().iterator();
    }

    public Object get(String extension) {
        return extensions.get(extension);
    }
}
