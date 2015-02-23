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
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.fs.ModeException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import org.xml.sax.SAXException;

import javax.naming.NoPermissionException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Status extends StageCommand {
    private static enum Key {
        NAME, DIRECTORY, WRAPPER, URL, TYPE, OWNER, TOMCAT, DEBUGGER, JCONSOLE, APPS;

        public String toString() {
            return name().toLowerCase();
        }

        public int length() {
            return name().length();
        }
    }

    private final List<Key> selected = new ArrayList<>();

    public Status(Session session) throws IOException {
        super(session);
    }

    @Remaining
    public void property(String str) {
        selected.add(Key.valueOf(str.toUpperCase()));
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        List<Key> keys;
        Map<Key, Object> status;
        int width;
        Object value;
        List<String> lst;
        boolean first;

        status = status(stage);
        keys = selected.isEmpty() ? Arrays.asList(Key.values()) : selected;
        width = 0;
        for (Key key : keys) {
            width = Math.max(width, key.length());
        }
        width += 2;
        for (Key key : keys) {
            console.info.print(Strings.times(' ', width - key.length()));
            console.info.print(key.toString());
            console.info.print(" : ");
            value = status.get(key);
            if (value == null) {
                console.info.println();
            } else if (value instanceof String) {
                console.info.println(value);
            } else {
                first = true;
                lst = (List<String>) value;
                for (String item : lst) {
                    if (first) {
                        first = false;
                    } else {
                        console.info.print(Strings.times(' ', width + 3));
                    }
                    console.info.println(item);
                }
            }
        }
    }

    private Map<Key, Object> status(Stage stage) throws IOException, SAXException {
        Map<Key, Object> result;
        Ports ports;

        result = new TreeMap<>();
        result.put(Key.NAME, stage.getName());
        result.put(Key.DIRECTORY, stage.getDirectory().getAbsolute());
        result.put(Key.WRAPPER, stage.wrapper.getAbsolute());
        result.put(Key.URL, stage.getUrl());
        result.put(Key.TYPE, stage.getType());
        result.put(Key.OWNER, stage.technicalOwner());
        ports = tomcatStatus(stage, result);
        result.put(Key.APPS, getAppUrls(stage));
        result.put(Key.JCONSOLE, ports == null ? null : session.configuration.hostname + ":" + ports.jmx());
        return result;
    }

    private Object getAppUrls(Stage stage) throws IOException, SAXException {
        FileNode file;

        file = stage.serverXml();
        if (file.exists()) {
            return new ArrayList<>(stage.urls(ServerXml.load(file)).values());
        } else {
            return "(unknown until first stage start)";
        }
    }

    private Ports tomcatStatus(Stage stage, Map<Key, Object> result) throws IOException {
        String tomcatPid;
        String debug;
        Ports ports;

        tomcatPid = stage.runningTomcat();
        result.put(Key.TOMCAT, daemonStatus(tomcatPid, stage.state()));
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
        } else {
            ports = null;
            debug = "off";
        }
        result.put(Key.DEBUGGER, debug);
        return ports;
    }

    private String daemonStatus(String pid, Stage.State state) {
        if (state == Stage.State.UP) {
            return "up (pid " + pid + ")";
        } else {
            return String.valueOf(state);
        }
    }
}
