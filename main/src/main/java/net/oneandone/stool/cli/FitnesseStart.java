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
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Launches Fitnesse (http://www.fitnesse.org) via
 * fitnesse-launchner-maven-plugin (https://code.google.com/archive/p/fitnesse-launcher-maven-plugin/)
 */
public class FitnesseStart extends StageCommand {
    public FitnesseStart(Session session) {
        super(false, session, Mode.EXCLUSIVE, Mode.EXCLUSIVE, Mode.SHARED);
    }

    @Override
    public boolean doBefore(List<Stage> stages, int indent) throws IOException {
        int global;
        int reserved;

        global = session.configuration.quota;
        if (global != 0) {
            reserved = session.quotaReserved();
            if (reserved > global) {
                throw new IOException("stage quotas exceed available disk space: " + reserved + " mb > " + global + " mb");
            }
        }
        return super.doBefore(stages, indent);
    }

    @Override
    public void doMain(Stage stage) throws Exception {
        Console console;
        Ports ports;
        Vhost host;
        int port;
        String url;
        FileNode log;
        Launcher launcher;

        if (stage.state() == Stage.State.UP) {
            throw new IOException("cannot start FitNesse because stage is up");
        }
        stage.modify();
        // to avoid running into a ping timeout below:
        stage.session.configuration.verfiyHostname();


        console = stage.session.console;
        ports = session.pool().allocate(stage, Collections.emptyMap());
        for (String vhost : stage.vhostNames()) {
            host = ports.lookup(vhost);
            port = host.httpPort();
            url = findUrl(stage, host);
            launcher = stage.launcher("mvn",
                    "uk.co.javahelp.fitnesse:fitnesse-launcher-maven-plugin:wiki", "-Dfitnesse.port=" + port);
            launcher.dir(stage.session.world.file(findProjectDir(ports, host)));

            log = stage.getBackstage().join("tomcat/logs/fitness-" + port + ".log");
            log.getParent().mkdirsOpt();
            if (!log.exists()) {
                log.mkfile();
            }
            // no exec -- keeps running until stopped; no way to detect failures
            // no log.close!
            launcher.launch(log.newWriter());
            console.info.println(vhost + " fitnesse started: " + url);
        }

    }

    private String findProjectDir(Ports ports, Vhost fitnesseHost) {
        String path;

        path = ports.lookup(fitnesseHost.name).docBase();
        return path.substring(0, path.indexOf("/target"));
    }

    private String findUrl(Stage stage, Vhost host) {
        return host.httpUrl(stage.session.configuration.vhosts, stage.session.configuration.hostname);
    }
}
