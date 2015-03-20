package net.oneandone.stool.extensions;

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.xml.XmlException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Extensions {
    private final List<Extension> extensions;

    public Extensions() {
        extensions = new ArrayList<>();
    }

    public void add(Extension extension) {
        extensions.add(extension);
    }

    public Map<String, FileNode> vhosts() {
        Map<String, FileNode> result;

        result = new HashMap<>();
        for (Extension extension : extensions) {
            result.putAll(extension.vhosts());
        }
        return result;
    }

    public void beforeStart(Collection<String> apps) throws IOException {
        for (Extension extension : extensions) {
            extension.beforeStart(apps);
        }
    }

    public Map<String, String> contextParameter(String host, int httpPort, FileNode webinf) throws XmlException {
        Map<String, String> result;

        result = new HashMap<>();
        for (Extension extension : extensions) {
            extension.contextParameter(host, httpPort, webinf, result);
        }
        return result;
    }
}
