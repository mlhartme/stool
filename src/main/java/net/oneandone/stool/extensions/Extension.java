package net.oneandone.stool.extensions;

import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.xml.XmlException;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public interface Extension {
    /**
     * @return vhost names mapped to a docroot. Docroot may be null which indicates that this vhost must not be added
     * to the tomcat configration (this is useful to only allocate ports)
     */
    Map<String, FileNode> vhosts(Stage stage) throws IOException;

    void beforeStart(Stage stage) throws IOException;

    void beforeStop(Stage stage) throws IOException;

    void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result) throws XmlException;
}
