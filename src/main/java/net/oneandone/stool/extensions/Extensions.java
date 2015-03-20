package net.oneandone.stool.extensions;

import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.xml.XmlException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Extensions implements Iterable<Extension> {
    private final List<Extension> extensions;

    public Extensions() {
        this.extensions = new ArrayList<>();
    }

    public void add(Extension extension) {
        extensions.add(extension);
    }

    public Map<String, FileNode> vhosts(Stage stage) {
        Map<String, FileNode> result;

        result = new HashMap<>();
        for (Extension extension : extensions) {
            result.putAll(extension.vhosts(stage));
        }
        return result;
    }

    public void beforeStart(Stage stage, Collection<String> apps) throws IOException {
        for (Extension extension : extensions) {
            extension.beforeStart(stage, apps);
        }
    }

    public Map<String, String> contextParameter(Stage stage, String host, int httpPort, FileNode webinf) throws XmlException {
        Map<String, String> result;

        result = new HashMap<>();
        for (Extension extension : extensions) {
            extension.contextParameter(stage, host, httpPort, webinf, result);
        }
        return result;
    }

    public void addAll(Extensions op) {
        extensions.addAll(op.extensions);
    }

    @Override
    public Iterator<Extension> iterator() {
        return extensions.iterator();
    }
}
