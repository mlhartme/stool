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
import net.oneandone.stool.util.Host;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Status extends StageCommand {
    public enum Field {
        ID, NAME, DIRECTORY, BACKSTAGE, URL, TYPE, STATE, OWNER, TOMCAT, DEBUGGER, JMX, APPS, OTHER;

        public String toString() {
            return name().toLowerCase();
        }

        public int length() {
            return name().length();
        }
    }

    private final List<Field> selected = new ArrayList<>();

    public Status(Session session) {
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
                if (first) {
                    console.info.println();
                }
            }
        }
    }

    public static Map<Field, Object> status(Stage stage) throws IOException {
        Map<Field, Object> result;
        Ports ports;
        List<String> jmx;
        String url;

        result = new TreeMap<>();
        result.put(Field.ID, stage.config().id);
        result.put(Field.NAME, stage.getName());
        result.put(Field.DIRECTORY, stage.getDirectory().getAbsolute());
        result.put(Field.BACKSTAGE, stage.backstage.getAbsolute());
        result.put(Field.URL, stage.getUrl());
        result.put(Field.TYPE, stage.getType());
        result.put(Field.OWNER, stage.owner());
        result.put(Field.STATE, stage.state().toString());
        ports = tomcatStatus(stage, result);
        result.put(Field.APPS, stage.namedUrls());
        result.put(Field.OTHER, other(stage, ports));
        jmx = new ArrayList<>();
        if (ports != null) {
            url = stage.session.configuration.hostname + ":" + ports.jmx();
            jmx.add("jconsole " + url);
            jmx.add("jvisualvm --openjmx " + url);
        }
        result.put(Field.JMX, jmx);
        return result;
    }

    /** TODO: we need this field to list fitnesse urls ...*/
    private static List<String> other(Stage stage, Ports ports) {
        List<String> result;

        result = new ArrayList<>();
        if (ports != null) {
            for (Host host : ports.hosts()) {
                if (host.isWebapp()) {
                    continue;
                }
                if (host.vhost.contains("+")) {
                    continue;
                }
                result.add(host.httpUrl(stage.session.configuration.vhosts));
            }
        }
        return result;
    }

    private static Ports tomcatStatus(Stage stage, Map<Field, Object> result) throws IOException {
        String tomcatPid;
        String debug;
        Ports ports;

        tomcatPid = stage.runningTomcat();
        result.put(Field.TOMCAT, tomcatPid);
        if (tomcatPid != null) {
            ports = stage.loadPortsOpt();
            if (ports == null) {
                debug = null;
            } else {
                if (stage.shared().join("conf/service-wrapper.conf").readString().contains("=-Xdebug\n")) {
                    debug = Integer.toString(ports.debug());
                } else {
                    debug = null;
                }
            }
        } else {
            ports = null;
            debug = null;
        }
        result.put(Field.DEBUGGER, debug);
        return ports;
    }
}
