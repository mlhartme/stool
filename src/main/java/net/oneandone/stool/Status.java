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
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.util.Strings;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Status extends StageCommand {
    private static enum Field {
        NAME, DIRECTORY, WRAPPER, URL, TYPE, OWNER, TOMCAT, DEBUGGER, JCONSOLE, APPS;

        public String toString() {
            return name().toLowerCase();
        }

        public int length() {
            return name().length();
        }
    }

    private final List<Field> selected = new ArrayList<>();

    public Status(Session session) throws IOException {
        super(session);
    }

    @Remaining
    public void field(String str) {
        try {
            selected.add(Field.valueOf(str.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new ArgumentException(str + ": no such status field, choose one of " + Arrays.asList(Field.values()));
        }
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        List<Field> fields;
        Map<Field, Object> status;
        int width;
        Object value;
        List<String> lst;
        boolean first;

        status = status(stage);
        fields = selected.isEmpty() ? Arrays.asList(Field.values()) : selected;
        width = 0;
        for (Field field : fields) {
            width = Math.max(width, field.length());
        }
        width += 2;
        for (Field field : fields) {
            console.info.print(Strings.times(' ', width - field.length()));
            console.info.print(field.toString());
            console.info.print(" : ");
            value = status.get(field);
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

    private Map<Field, Object> status(Stage stage) throws IOException, SAXException {
        Map<Field, Object> result;
        Ports ports;

        result = new TreeMap<>();
        result.put(Field.NAME, stage.getName());
        result.put(Field.DIRECTORY, stage.getDirectory().getAbsolute());
        result.put(Field.WRAPPER, stage.wrapper.getAbsolute());
        result.put(Field.URL, stage.getUrl());
        result.put(Field.TYPE, stage.getType());
        result.put(Field.OWNER, stage.technicalOwner());
        ports = tomcatStatus(stage, result);
        result.put(Field.APPS, stage.urls());
        result.put(Field.JCONSOLE, ports == null ? null : session.configuration.hostname + ":" + ports.jmx());
        return result;
    }

    private Ports tomcatStatus(Stage stage, Map<Field, Object> result) throws IOException {
        String tomcatPid;
        String debug;
        Ports ports;

        tomcatPid = stage.runningTomcat();
        result.put(Field.TOMCAT, daemonStatus(tomcatPid, stage.state()));
        if (tomcatPid != null) {
            ports = stage.loadPortsOpt();
            if (ports == null) {
                debug = "off";
            } else {
                try {
                    if (stage.shared().join("conf/service-wrapper.conf").readString().contains("=-Xdebug\n")) {
                        debug = "on (port " + ports.debug() + ")";
                    } else {
                        debug = "off";
                    }
                } catch (IOException e) {
                    debug = "unknown";
                }
            }
        } else {
            ports = null;
            debug = "off";
        }
        result.put(Field.DEBUGGER, debug);
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
