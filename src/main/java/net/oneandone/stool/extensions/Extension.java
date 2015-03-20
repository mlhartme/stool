package net.oneandone.stool.extensions;

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.xml.XmlException;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public interface Extension {
    Map<String, FileNode> vhosts();

    void beforeStart(Collection<String> apps) throws IOException;

    void contextParameter(String host, int httpPort, FileNode webinf, Map<String, String> result) throws XmlException;
}
