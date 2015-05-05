package net.oneandone.stool.extensions;

import net.oneandone.stool.stage.SourceStage;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Host;
import net.oneandone.stool.util.Ports;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import net.oneandone.sushi.xml.XmlException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Fitnesse implements Extension {
    private static final String FITNESSSE_PREFIX = "fitnesse.";

    @Override
    public Map<String, FileNode> vhosts(Stage stage) throws IOException {
        Map<String, FileNode> result;

        if (!(stage instanceof SourceStage)) {
            throw new UnsupportedOperationException("stage type not supported: " + stage.getClass());
        }
        result = new HashMap<>();
        for (String vhost : stage.hosts().keySet()) {
            result.put(FITNESSSE_PREFIX + vhost, null);
        }
        return result;
    }

    @Override
    public void beforeStart(Stage stage) throws IOException {
        Console console;
        Ports ports;
        Host host;
        int port;
        String projectDir;
        String url;

        console = stage.session.console;
        ports = stage.loadPortsOpt();
        for (String vhost : stage.hosts().keySet()) {
            host = ports.lookup(FITNESSSE_PREFIX + vhost);
            projectDir = findProjectDir(ports, host);
            port = host.httpPort();
            stage.launcher("sh", projectDir + "/src/test/resources/fitnesse-start.sh", String.valueOf(port), projectDir).exec(console.info, console.error);
            url = findUrl(host);
            console.info.println("fitnesse start: " + url.concat(":" + port));
        }
    }

    private String findProjectDir(Ports ports, Host fitnesseHost) {
        String path;

        path = ports.lookup(Strings.removeLeft(fitnesseHost.vhost, FITNESSSE_PREFIX)).docBase();
        return path.substring(0, path.indexOf("/target"));
    }

    @Override
    public void beforeStop(Stage stage) throws IOException {
        Console console;
        Ports ports;
        Host host;
        int port;

        console = stage.session.console;
        ports = stage.loadPortsOpt();
        for (String vhost : stage.hosts().keySet()) {
            host = ports.lookup(FITNESSSE_PREFIX + vhost);
            port = host.httpPort();
            String url = findUrl(host);
            String projectDir = findProjectDir(ports, host);
            stage.launcher("sh", projectDir + "/src/test/resources/fitnesse-stop.sh", url, String.valueOf(port), projectDir).exec(console.info,console.error);
            console.info.println("fitnesse stop: " + url.concat(":" + port));
        }
    }

    private String findUrl(Host host) {
        return host.httpUrl(false);
    }

    @Override
    public void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result) throws XmlException {
    }
}
