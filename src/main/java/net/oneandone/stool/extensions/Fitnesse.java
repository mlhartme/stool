package net.oneandone.stool.extensions;

import net.oneandone.stool.stage.SourceStage;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Host;
import net.oneandone.stool.util.Ports;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Strings;
import net.oneandone.sushi.xml.XmlException;

import java.io.File;
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
        String url;
        Launcher launcher;
        Process process;

        console = stage.session.console;
        ports = stage.loadPortsOpt();
        for (String vhost : stage.hosts().keySet()) {
            host = ports.lookup(FITNESSSE_PREFIX + vhost);
            port = host.httpPort();
            url = findUrl(host);
            launcher = stage.launcher("mvn", "-P", "fitnesse uk.co.javahelp.fitnesse:fitnesse-launcher-maven-plugin:wiki", "-Dfitnesse.port=" + port);
            launcher.getBuilder().redirectOutput(new File("target/fitness-" + port + ".out"));
            launcher.getBuilder().redirectError(new File("target/fitness-" + port + ".err"));
            process = launcher.getBuilder().start();
            console.info.println("fitnesse started (" + process + "): " + url.concat(":" + port));
        }
    }

    @Override
    public void beforeStop(Stage stage) throws IOException {
        Console console;
        Ports ports;
        Host host;

        console = stage.session.console;
        ports = stage.loadPortsOpt();
        for (String vhost : stage.hosts().keySet()) {
            host = ports.lookup(FITNESSSE_PREFIX + vhost);
            stage.launcher("curl " + findUrl(host) + ":" + host.httpPort() + "?responder=shutdown").exec(console.verbose);
        }
    }

    private String findUrl(Host host) {
        return host.httpUrl(false);
    }

    @Override
    public void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result) throws XmlException {
    }
}
