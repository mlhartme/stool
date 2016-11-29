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
package net.oneandone.stool.cli;

import net.oneandone.inline.Console;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Session;
import net.oneandone.stool.util.Vhost;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Launches Fitnesse (http://www.fitnesse.org) via
 * fitnesse-launchner-maven-plugin (https://code.google.com/archive/p/fitnesse-launcher-maven-plugin/)
 */
public class FitnesseStop extends StageCommand {
    public FitnesseStop(Session session) {
        super(false, session, Mode.EXCLUSIVE, Mode.EXCLUSIVE, Mode.SHARED);
    }

    @Override
    public void doMain(Stage stage) throws Exception {
        Console console;
        Ports ports;
        Vhost host;
        String url;

        console = stage.session.console;
        ports = stage.loadPortsOpt();

        stage.modify();
        for (String vhost : stage.vhostNames()) {
            host = ports.lookup(vhost);
            if (host == null) {
                console.error.println("vhost not found: " + vhost);
            } else {
                url = findUrl(stage, host);
                if (isFitnesseServerUp(url)) {
                    console.verbose.println(stage.session.world.validNode(url + "?responder=shutdown").readString());
                } else {
                    console.info.println("fitnesse server is already down: " + vhost);
                }
            }
        }
    }

    private boolean isFitnesseServerUp(String urlStr) throws IOException {
        URL url;
        HttpURLConnection conn;

        url = new URL(urlStr);
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        try {
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String findUrl(Stage stage, Vhost host) {
        return host.httpUrl(stage.session.configuration.vhosts, stage.session.configuration.hostname);
    }
}
