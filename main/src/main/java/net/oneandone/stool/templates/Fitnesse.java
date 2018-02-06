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

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Vhost;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Fitnesse implements Template {
    @Override
    public Map<String, FileNode> vhosts(Stage stage) {
        return new HashMap<>();
    }

    @Override
    public void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result) {
    }

    @Override
    public void tomcatOpts(Stage stage, Map<String, String> result) {
    }

    @Override
    public void containerOpts(Stage stage, Map<String, Object> result) throws IOException {

        result.put("fitnesse", true);
        result.put("fitnesse_command", cmd(stage));
    }

    /**
     *  Launches Fitnesse Wiki (http://www.fitnesse.org).
     *
     * Fitnesse wiki does not implement the servlet interfaces, so I cannot use the normal startup code for tomcats.
     * Instead, I invoke fitnesse-launchner-maven-plugin (https://code.google.com/archive/p/fitnesse-launcher-maven-plugin/)
     * to launch the embedded web server.
     */
    private String cmd(Stage stage) throws IOException {
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

    @Override
    public void files(Stage stage, FileNode dest) throws IOException {
    }
}
