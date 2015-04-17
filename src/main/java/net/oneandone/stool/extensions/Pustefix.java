package net.oneandone.stool.extensions;

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Files;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.xml.XmlException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Pustefix implements Extension {
    private final String mode;

    public Pustefix() {
        this("test");
    }

    public Pustefix(String mode) {
        this.mode = mode;
    }

    @Override
    public Map<String, FileNode> vhosts(Stage stage) {
        return new HashMap<>();
    }

    private static final String APPLOGS = "tomcat/logs/applogs";

    @Override
    public void beforeStart(Stage stage, Collection<String> apps) throws IOException {
        Files.stoolDirectory(stage.shared().join(APPLOGS).mkdirOpt());
    }

    @Override
    public void beforeStop(Stage stage) throws IOException {
    }

    @Override
    public void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result) throws XmlException {
        String app;

        app = host.substring(0, host.indexOf('.'));
        result.put("mode", mode);
        result.put("logroot", stage.shared().join(APPLOGS, app).getAbsolute());
    }
}
