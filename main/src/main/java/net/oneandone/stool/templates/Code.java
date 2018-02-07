/*
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
package net.oneandone.stool.templates;

import net.oneandone.inline.Console;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.ServerXml;
import net.oneandone.stool.util.Vhost;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class Code {
    // tomcat

    private static final String APPLOGS = "tomcat/logs/applogs";

    // TODO
    public void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result, String mode) {
        String app;

        app = host.substring(0, host.indexOf('.'));
        result.put("mode", mode);
        result.put("logroot", ServerXml.toMount(stage.getDirectory(), stage.getBackstage().join(APPLOGS, app).getAbsolute()));
    }


    // TODO: have a list of projects; always prepend @
    public static String faultProjects(String faultProject) {
        StringBuilder result;

        if (faultProject.isEmpty()) {
            return faultProject;
        }
        result = new StringBuilder();
        for (String entry : Separator.SPACE.split(faultProject)) {
            if (result.length() > 0) {
                result.append(' ');
            }
            if (!entry.startsWith("@")) {
                result.append('@');
            }
            result.append(entry);
        }
        return result.toString();
    }

    public static void faultWorkspace(Stage stage, FileNode dir, String projects) throws IOException {
        Console console;
        Launcher launcher;

        launcher = stage.launcher("fault");
        console = stage.session.console;
        if (console.getVerbose()) {
            launcher.arg("-v");
        }
        launcher.arg("-auth=false");
        launcher.arg("run", projects, "cp", "-r", dir.getWorld().getHome().join(".fault").getAbsolute(), dir.getAbsolute());
        console.verbose.println("executing " + launcher);
        console.verbose.println(launcher.exec());
    }

    //--

    /**
     *  Launches Fitnesse Wiki (http://www.fitnesse.org).
     *
     * Fitnesse wiki does not implement the servlet interfaces, so I cannot use the normal startup code for tomcats.
     * Instead, I invoke fitnesse-launchner-maven-plugin (https://code.google.com/archive/p/fitnesse-launcher-maven-plugin/)
     * to launch the embedded web server.
     */
    public static String fitnesseCommand(Stage stage) throws IOException {
        StringBuilder result;
        Ports ports;
        Vhost host;
        int port;

        result = new StringBuilder();
        ports = stage.session.pool().allocate(stage, Collections.emptyMap());
        for (String vhost : stage.vhostNames()) {
            host = ports.lookup(vhost);
            port = host.httpPort();
            if (result.length() == 0) {
                result.append("CMD ");
            } else {
                result.append("&& \\\n    ");
            }
            FileNode dir = stage.session.world.file(findProjectDir(ports, host));
            result.append("cd " + dir.getAbsolute());
            result.append(" && ");
            result.append("mvn ");
            result.append("--settings /Users/mhm/.m2/settings.xml "); // TODO
            result.append("uk.co.javahelp.fitnesse:fitnesse-launcher-maven-plugin:wiki -Dfitnesse.port=" + port);
        }
        result.append("\n");
        return result.toString();
    }

    private static String findProjectDir(Ports ports, Vhost fitnesseHost) {
        String path;

        path = ports.lookup(fitnesseHost.name).docBase();
        return path.substring(0, path.indexOf("/target"));
    }
}
