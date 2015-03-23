package net.oneandone.stool.extensions;

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Ports;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.xml.XmlException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Fitnesse implements Extension {
    private static final String FITNESSSE = "fitnesse";

    private boolean enabled;

    @Override
    public Map<String, FileNode> vhosts(Stage stage) {
        Map<String, FileNode> result;

        result = new HashMap<>();
        if (enabled) {
            result.put(FITNESSSE, null);
        }
        return result;
    }

    @Override
    public void beforeStart(Stage stage, Collection<String> apps) throws IOException {
        Console console;
        Ports ports;
        int port;

        if (enabled) {
            console = stage.session.console;
            ports = stage.loadPortsOpt();
            port = ports.lookup(FITNESSSE).httpPort();
            console.info.println("start port " + port);
        }
    }

    @Override
    public void beforeStop(Stage stage) throws IOException {
        Console console;
        Ports ports;
        int port;

        if (enabled) {
            console = stage.session.console;
            ports = stage.loadPortsOpt();
            port = ports.lookup(FITNESSSE).httpPort();
            console.info.println("stop port " + port);
        }
    }

    @Override
    public void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result) throws XmlException {
    }
}
