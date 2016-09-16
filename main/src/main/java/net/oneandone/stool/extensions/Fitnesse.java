/**
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.stool.extensions;

import net.oneandone.inline.Console;
import net.oneandone.stool.stage.SourceStage;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Vhost;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Launches Fitnesse (http://www.fitnesse.org) via
 * fitnesse-launchner-maven-plugin (https://code.google.com/archive/p/fitnesse-launcher-maven-plugin/)
 */
public class Fitnesse implements Extension {
    private static final String FITNESSSE_PREFIX = "fitnesse.";

    @Override
    public Map<String, FileNode> vhosts(Stage stage) throws IOException {
        Map<String, FileNode> result;

        if (!(stage instanceof SourceStage)) {
            throw new UnsupportedOperationException("stage type not supported: " + stage.getClass());
        }
        result = new HashMap<>();
        for (String vhost : stage.vhostNames()) {
            result.put(FITNESSSE_PREFIX + vhost, null);
        }
        return result;
    }

    @Override
    public void beforeStart(Stage stage) throws IOException {
        Console console;
        Ports ports;
        Vhost host;
        int port;
        String url;
        FileNode log;
        Launcher launcher;

        console = stage.session.console;
        ports = stage.loadPortsOpt();
        for (String vhost : stage.vhostNames()) {
            host = ports.lookup(FITNESSSE_PREFIX + vhost);
            port = host.httpPort();
            url = findUrl(stage, host);
            launcher = stage.launcher("mvn",
                    "uk.co.javahelp.fitnesse:fitnesse-launcher-maven-plugin:wiki", "-Dfitnesse.port=" + port);
            launcher.dir(stage.session.world.file(findProjectDir(ports, host)));

            log = stage.getBackstage().join("tomcat/logs/fitness-" + port + ".log");
            if (!log.exists()) {
                log.mkfile();
                Files.stoolFile(log);
            }
            // no exec -- keeps running until stopped; no way to detect failures
            // no log.close!
            launcher.launch(log.newWriter());
            console.info.println(vhost + " fitnesse started: " + url);
        }
    }

    private String findProjectDir(Ports ports, Vhost fitnesseHost) {
        String path;

        path = ports.lookup(Strings.removeLeft(fitnesseHost.name, FITNESSSE_PREFIX)).docBase();
        return path.substring(0, path.indexOf("/target"));
    }

    @Override
    public void beforeStop(Stage stage) throws IOException {
        Console console;
        Ports ports;
        Vhost host;
        String url;

        console = stage.session.console;
        ports = stage.loadPortsOpt();

        for (String vhost : stage.vhostNames()) {
            host = ports.lookup(FITNESSSE_PREFIX + vhost);
            if (host == null) {
                // ignore: fitnesse was started for an already running stage
            } else {
                url = findUrl(stage, host);
                if (isFitnesseServerUp(url, console)) {
                    console.verbose.println(stage.session.world.validNode(url + "?responder=shutdown").readString());
                }
            }
        }
    }

    private boolean isFitnesseServerUp(String urlStr, Console console) throws IOException {
        URL url;
        HttpURLConnection conn;

        url = new URL(urlStr);
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        try {
            if (conn.getResponseCode() == 200) {
                return true;
            }
        } catch (Exception e) {
           // do nothing
        }
        console.info.println("fitnesse server is already down: " + urlStr);
        return false;
    }

    private String findUrl(Stage stage, Vhost host) {
        return host.httpUrl(stage.session.configuration.vhosts, stage.session.configuration.hostname);
    }

    @Override
    public void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result) {
    }
}
