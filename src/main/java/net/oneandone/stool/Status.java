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
package net.oneandone.stool;

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.ServerXml;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;
import org.xml.sax.SAXException;

import java.io.IOException;

public class Status extends StageCommand {
    public Status(Session session) throws IOException {
        super(session);
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        Ports ports;

        header(stage.getName());
        message("name:      " + stage.getName());
        message("directory: " + stage.getDirectory().getAbsolute());
        message("wrapper:   " + stage.wrapper.getAbsolute());
        message("tomcat:    " + stage.catalinaBase().getAbsolute());
        message("url:       " + stage.getUrl());
        message("type:      " + stage.getType());
        message("owner:     " + stage.technicalOwner());
        message("");
        ports = showDaemonsFrom(stage);
        message("");
        header("app urls");
        message(getAppUrlsFrom(stage));
        message("");
        if (ports != null) {
            header("jconsole " + session.stoolConfiguration.hostname + ":" + ports.jmx());
        }
    }

    private String getAppUrlsFrom(Stage stage) throws IOException, SAXException {
        FileNode serverXmlFile;

        serverXmlFile = stage.serverXml();
        if (serverXmlFile.exists()) {
            return stage.urls(ServerXml.load(serverXmlFile)).values()
              .toString().replaceAll(", ", "\\\n").replace("[", "").replace("]", "");
        } else {
            return "(unknown until first stage start)";
        }
    }

    private Ports showDaemonsFrom(Stage stage) throws IOException {
        String tomcatPid;
        String debug;
        Ports ports;

        tomcatPid = stage.runningTomcat();
        showDaemon("tomcat", tomcatPid, stage.state());
        if (tomcatPid != null) {
            ports = stage.loadPorts();
            try {
                if (stage.getDirectory().exec("ps", "u", "-p", tomcatPid).contains("-Xdebug")) {
                    debug = "on (port " + ports.debug() + ")";
                } else {
                    debug = "off";
                }
            } catch (IOException e) {
                debug = "unknown";
            }
            if (debug != null) {
                message("debugger: " + debug);
            }
        } else {
            ports = null;
        }
        return ports;
    }

    private void showDaemon(String name, String pid, Stage.State state) {
        String status;

        if (state != Stage.State.DOWN && state != Stage.State.CRASHED) {
            status = "up (pid " + pid + ")";
            if (state == Stage.State.SLEEPING) {
                status = status + " AND sleeping";
            }
        } else {
            status = String.valueOf(state);
        }
        message(name + ": " + status);
    }
}
