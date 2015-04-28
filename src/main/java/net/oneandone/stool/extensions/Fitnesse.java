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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Fitnesse implements Extension {
    private static final String FITNESSSE = "fitnesse";

    @Override
    public Map<String, FileNode> vhosts(Stage stage) {
        Map<String, FileNode> result;

        result = new HashMap<>();
        result.put(FITNESSSE, null);
        return result;
    }

    @Override
    public void beforeStart(Stage stage, Collection<String> apps) throws IOException {
        Console console;
        Ports ports;
        int port;
        console = stage.session.console;
        ports = stage.loadPortsOpt();
        port = ports.lookup(FITNESSSE).httpPort();
        String projectDir = findProjectDir(ports);

        stage.launcher("sh", projectDir + "/src/test/resources/fitnesse-start.sh", String.valueOf(port), projectDir).exec(console.info,console.error);
        String url = findUrl(ports);
        console.info.println("fitnesse start: " + url.concat(":" + port));
    }

    private String findProjectDir(Ports ports) {
        String path = ports.mainHost().docBase();
        return path.substring(0, path.indexOf("/target"));
    }

    @Override
    public void beforeStop(Stage stage) throws IOException {
        Console console;
        Ports ports;
        int port;

        console = stage.session.console;
        ports = stage.loadPortsOpt();
        port = ports.lookup(FITNESSSE).httpPort();
        String url = findUrl(ports);
        String projectDir = findProjectDir(ports);
        projectDir = hackForProjectDir(projectDir);

        stage.launcher("sh",projectDir + "/src/test/resources/fitnesse-stop.sh", url, String.valueOf(port), projectDir).exec(console.info,console.error);
        console.info.println("fitnesse stop: " + url.concat(":" + port));
    }


    private String hackForProjectDir(String projectDir){
        try{
            String[] split = projectDir.split("/");
            //project dir includes duplicates
            if(split[split.length - 1].equals(split[split.length -2])) {
                return projectDir.substring(0, projectDir.lastIndexOf(("/" + split[split.length - 2])));
            }
        }catch (Exception e) {
            return projectDir;
        }
        return projectDir;
    }

    private String findUrl(Ports ports) {
        String url = ports.mainHost().httpUrl(false);
        Pattern pattern = Pattern.compile("(:[0-9]+)");
        Matcher matcher = pattern.matcher(url);
        matcher.find();
        String port = matcher.group();
        return url.substring(0, url.indexOf(port)); //exclude port
    }

    @Override
    public void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result) throws XmlException {
    }
}
