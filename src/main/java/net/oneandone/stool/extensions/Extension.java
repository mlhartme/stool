package net.oneandone.stool.extensions;

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.xml.XmlException;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public interface Extension {
    Map<String, FileNode> vhosts();

    void beforeStart(Collection<String> apps) throws IOException;

    void contextParameter(int httpPort, String name, Element context, FileNode webinf) throws XmlException;
}
