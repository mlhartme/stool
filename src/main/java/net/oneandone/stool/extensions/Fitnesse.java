package net.oneandone.stool.extensions;

import net.oneandone.stool.stage.SourceStage;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Host;
import net.oneandone.stool.util.Ports;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.xml.XmlException;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static net.oneandone.sushi.util.Strings.removeLeft;

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

        console = stage.session.console;
        ports = stage.loadPortsOpt();
        for (String vhost : stage.hosts().keySet()) {
            host = ports.lookup(FITNESSSE_PREFIX + vhost);
            port = host.httpPort();
            url = findUrl(stage, host);
            String projectDir = findProjectDir(ports, host);
            launcher = stage.launcher("mvn",
                    "uk.co.javahelp.fitnesse:fitnesse-launcher-maven-plugin:wiki",
                    "-Dfitnesse.port=" + port);
            launcher.dir(console.world.file(findProjectDir(ports, host)));

            File output = new File(projectDir + "/target/fitness-" + port + ".out");
            File error = new File(projectDir + "/target/fitness-" + port + ".err");
            if (!output.exists()) {
                output.createNewFile();
                error.createNewFile();
            }

            launcher.getBuilder().redirectOutput(output);
            launcher.getBuilder().redirectError(error);
            launcher.getBuilder().start();
            console.info.println(vhost + " fitnesse started: " + url);
        }
    }

    private String findProjectDir(Ports ports, Host fitnesseHost) {
        String path;

        path = ports.lookup(removeLeft(fitnesseHost.vhost, FITNESSSE_PREFIX)).docBase();
        return path.substring(0, path.indexOf("/target"));
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
            String url = findUrl(stage, host);
            if (isFitnesseServerUp(url, console)) {
                stage.launcher("curl", url + "?responder=shutdown").exec(console.verbose);
            }
        }
    }

    public boolean isFitnesseServerUp(String urlPrm, Console console) throws IOException {
        URL url = new URL(urlPrm);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        try {
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                return true;
            }
        } catch (Exception e) {
           //do nothing
        }
        console.info.println("fitnesse server is already down");
        return false;
    }

    private String findUrl(Stage stage, Host host) {
        return host.httpUrl(stage.session.configuration.vhosts);
    }

    @Override
    public void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result)
            throws XmlException {
    }
}
