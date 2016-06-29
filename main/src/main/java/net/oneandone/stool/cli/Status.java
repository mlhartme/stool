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

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Processes;
import net.oneandone.stool.util.Session;
import net.oneandone.stool.util.Vhost;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Status extends StageCommand {
    public enum Field {
        ID, NAME, SELECTED, DIRECTORY, BACKSTAGE, URL, TYPE, OWNER, DISK, STATE, UPTIME, CPU, MEM, SERVICE, TOMCAT, DEBUGGER, SUSPEND, JMX, APPS, OTHER;

        public String toString() {
            return name().toLowerCase();
        }

        public int length() {
            return name().length();
        }
    }

    private final List<Field> selected = new ArrayList<>();

    public Status(Session session) {
        super(session, Mode.SHARED, Mode.SHARED, Mode.SHARED);
    }

    public void field(String str) {
        try {
            selected.add(Field.valueOf(str.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new ArgumentException(str + ": no such status field, choose one of " + Arrays.asList(Field.values()));
        }
    }

    private static Separator TAB = Separator.on('\t');

    @Override
    public void doRun(Stage stage) throws Exception {
        List<Field> fields;
        Map<Field, Object> status;
        int width;
        boolean first;
        String value;

        status = status(session, processes(), stage);
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
            first = true;
            value = toString(status.get(field));
            if (value.isEmpty()) {
                console.info.println();
            } else for (String str : TAB.split(value)) {
                if (first) {
                    first = false;
                } else {
                    console.info.print(Strings.times(' ', width + 3));
                }
                console.info.println(str);
            }
        }
    }

    public static String toString(Object value) {
        boolean first;
        List<Object> lst;
        StringBuilder builder;

        if (value == null) {
            return "";
        } else if (value instanceof List) {
            first = true;
            lst = (List) value;
            builder = new StringBuilder();
            for (Object item : lst) {
                if (first) {
                    first = false;
                } else {
                    builder.append('\t');
                }
                builder.append(toString(item));
            }
            return builder.toString();
        } else {
            return value.toString();
        }
    }

    public static Map<Field, Object> status(Session session, Processes processes, Stage stage) throws IOException {
        Map<Field, Object> result;
        Ports ports;
        List<String> jmx;
        String url;

        result = new TreeMap<>();
        result.put(Field.ID, stage.config().id);
        result.put(Field.NAME, stage.getName());
        result.put(Field.SELECTED, session.isSelected(stage) ? "*" : "");
        result.put(Field.DIRECTORY, stage.getDirectory().getAbsolute());
        result.put(Field.BACKSTAGE, stage.backstage.getAbsolute());
        result.put(Field.DISK, Integer.toString(stage.diskUsed()));
        result.put(Field.URL, stage.getUrl());
        result.put(Field.TYPE, stage.getType());
        result.put(Field.OWNER, stage.owner());
        result.put(Field.UPTIME, stage.uptime());
        result.put(Field.STATE, stage.state().toString());
        ports = processStatus(processes, stage, result);
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
            for (Vhost vhost : ports.vhosts()) {
                if (vhost.isWebapp()) {
                    continue;
                }
                if (vhost.name.contains("+")) {
                    continue;
                }
                result.add(vhost.httpUrl(stage.session.configuration.vhosts, stage.session.configuration.hostname));
            }
        }
        return result;
    }

    public static Ports processStatus(Processes processes, Stage stage, Map<Field, Object> result) throws IOException {
        int servicePid;
        int tomcatPid;
        String debug;
        boolean suspend;
        Ports ports;
        String config;
        Double cpu;
        Double mem;

        servicePid = stage.runningService();
        if (servicePid != 0) {
            tomcatPid = processes.oneChild(servicePid);
            cpu = processes.lookup(tomcatPid).cpu;
            mem = processes.lookup(tomcatPid).mem;
            ports = stage.loadPortsOpt();
            if (ports == null) {
                debug = null;
                suspend = false;
            } else {
                config = stage.shared().join("conf/service-wrapper.conf").readString();
                if (config.contains("=-Xdebug\n")) {
                    debug = Integer.toString(ports.debug());
                } else {
                    debug = null;
                }
                suspend = debug != null && config.contains(",suspend=y");
            }
        } else {
            tomcatPid = 0;
            cpu = null;
            mem = null;
            ports = null;
            debug = null;
            suspend = false;
        }
        result.put(Field.CPU, cpu);
        result.put(Field.MEM, mem);
        result.put(Field.SERVICE, opt(servicePid));
        result.put(Field.TOMCAT, opt(tomcatPid));
        result.put(Field.DEBUGGER, debug);
        result.put(Field.SUSPEND, suspend);
        return ports;
    }

    private static Integer opt(int i) {
        return i == 0 ? null : i;
    }
}
