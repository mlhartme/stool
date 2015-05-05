package net.oneandone.stool.extensions;

import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.xml.XmlException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Extensions {
    public final Map<String, Switch> extensions;

    public Extensions() {
        this.extensions = new HashMap<>();
    }

    public void add(String name, boolean enabled, Extension extension) {
        if (extension == null) {
            throw new IllegalArgumentException();
        }
        if (extensions.put(name, new Switch(enabled, extension)) != null) {
            throw new IllegalArgumentException("duplicate extension: " + name);
        }
    }

    public Map<String, FileNode> vhosts(Stage stage) throws IOException {
        Map<String, FileNode> result;

        result = new HashMap<>();
        for (Switch s : extensions.values()) {
            if (s.enabled) {
                result.putAll(s.extension.vhosts(stage));
            }
        }
        return result;
    }

    public void beforeStart(Stage stage) throws IOException {
        for (Switch s : extensions.values()) {
            if (s.enabled) {
                s.extension.beforeStart(stage);
            }
        }
    }

    public void beforeStop(Stage stage) throws IOException {
        for (Switch s : extensions.values()) {
            if (s.enabled) {
                s.extension.beforeStop(stage);
            }
        }
    }

    /** @param host  the vhost name, even if global vhosts config is false */
    public Map<String, String> contextParameter(Stage stage, String host, int httpPort, FileNode webinf) throws XmlException {
        Map<String, String> result;

        result = new HashMap<>();
        for (Switch s : extensions.values()) {
            if (s.enabled) {
                s.extension.contextParameter(stage, host, httpPort, webinf, result);
            }
        }
        return result;
    }

    public Switch get(String extension) {
        return extensions.get(extension);
    }
}
