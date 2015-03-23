package net.oneandone.stool.extensions;

import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.xml.XmlException;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public interface Extension {
    Map<String, FileNode> vhosts(Stage stage);

    void beforeStart(Stage stage, Collection<String> apps) throws IOException;
    void beforeStop(Stage stage) throws IOException;

    void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result) throws XmlException;
}
