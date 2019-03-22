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
package net.oneandone.stool.cli;

import com.google.gson.JsonObject;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.docker.Engine;
import net.oneandone.stool.docker.Stats;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Image;
import net.oneandone.stool.stage.Reference;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Server;
import net.oneandone.stool.util.Session;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class App extends StageCommand {
    private final List<String> names;

    public App(Server server, List<String> names) {
        super(server, Mode.NONE, Mode.EXCLUSIVE);
        this.names = names;
    }

    private List<String> selection(Collection<String> available) {
        List<String> result;

        if (names.isEmpty()) {
            result = new ArrayList<>(available);
            Collections.sort(result);
        } else {
            result = new ArrayList<>();
            for (String name : names) {
                if (available.contains(name)) {
                    result.add(name);
                } else {
                    throw new ArgumentException("unknown app: " + name);
                }
            }
        }
        return result;
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        Stage stage;
        Map<String, List<Image>> all;
        Map<String, Stage.Current> currentMap;
        Engine engine;
        String marker;
        int idx;
        Stage.Current current;
        Ports ports;

        stage = server.session.load(reference);
        engine = stage.session.dockerEngine();
        all = stage.images(engine);
        currentMap = stage.currentMap();
        for (Map.Entry<String, List<Image>> entry : all.entrySet()) {
            if (!currentMap.containsKey(entry.getKey())) {
                currentMap.put(entry.getKey(), new Stage.Current(entry.getValue().get(0), null));
            }
        }
        for (String app : selection(all.keySet())) {
            current = currentMap.get(app);
            idx = 0;
            ports = stage.loadPorts().get(app);
            console.info.println("app:       " + app);
            console.info.println("cpu:       " + cpu(current));
            console.info.println("mem:       " + mem(current));
            console.info.println("container: " + current.container);
            console.info.println("origin:    " + current.image.origin);
            console.info.println("uptime:    " + uptime(current));
            console.info.println("heap:      " + heap(stage, app, current));
            console.info.println("disk-used: " + diskUsed(current));
            if (ports != null) {
                if (ports.debug != -1) {
                    console.info.println("debug port " + ports.debug);
                }
                if (ports.jmxmp != -1) {
                    String url;

                    console.info.println("jmx port:  " + ports.jmxmp);
                    url = stage.session.configuration.hostname + ":" + ports.jmxmp;
                    console.info.println("                 jconsole " + url);
                    console.info.println("                 jvisualvm --openjmx " + url);
                }
            }
            console.info.println();

            for (Image image : all.get(app)) {
                marker = image.id.equals(current.image.id) ? "==>" : "   ";
                console.info.printf("%s [%d] %s\n", marker, idx, image.id);
                console.info.println("       comment:    " + image.comment);
                console.info.println("       origin:     " + image.origin);
                console.info.println("       created-at: " + image.created);
                console.info.println("       created-by: " + image.createdBy);
                console.info.println("       created-on: " + image.createdOn);
                console.info.print("       secrets:    ");
                for (String key : image.secrets.keySet()) {
                    console.info.print(key);
                    console.info.print(' ');
                }
                console.info.println();
                idx++;
            }
            stage.rotateLogs(console);

        }
    }

    public String heap(Stage stage, String app, Stage.Current current) throws IOException {
        String container;
        JMXServiceURL url;
        MBeanServerConnection connection;
        ObjectName name;
        CompositeData result;
        long used;
        long max;

        container = current.container;
        if (container == null) {
            return "";
        }
        if (current.image.ports.jmxmp == -1) {
            return "[no jmx port]";
        }

        // see https://docs.oracle.com/javase/tutorial/jmx/remote/custom.html
        try {
            url = new JMXServiceURL("service:jmx:jmxmp://" + stage.session.configuration.hostname + ":" + stage.loadPorts().get(app).jmxmp);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        try {
            connection = JMXConnectorFactory.connect(url, null).getMBeanServerConnection();
        } catch (IOException e) {
            e.printStackTrace(stage.session.console.verbose);
            return "[cannot connect jmx server: " + e.getMessage() + "]";
        }
        try {
            name = new ObjectName("java.lang:type=Memory");
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException(e);
        }
        try {
            result = (CompositeData) connection.getAttribute(name, "HeapMemoryUsage");
        } catch (Exception e) {
            return "[cannot get jmx attribute: " + e.getMessage() + "]";
        }
        used = (Long) result.get("used");
        max = (Long) result.get("max");
        return Float.toString(((float) (used * 1000 / max)) / 10);
    }

    public int diskUsed(Stage.Current current) throws IOException {
        String container;
        JsonObject obj;

        container = current.container;
        if (container == null) {
            return 0;
        }
        obj = server.session.dockerEngine().containerInspect(container, true);
        // not SizeRootFs, that's the image size plus the rw layer
        return (int) (obj.get("SizeRw").getAsLong() / (1024 * 1024));
    }

    private String uptime(Stage.Current current) throws IOException {
        String container;

        container = current.container;
        return container == null ? null : Stage.timespan(server.session.dockerEngine().containerStartedAt(container));
    }

    private Integer cpu(Stage.Current current) throws IOException {
        Engine engine;
        Stats stats;
        String container;

        container = current.container;
        if (container == null) {
            return null;
        }
        engine = server.session.dockerEngine();
        stats = engine.containerStats(container);
        if (stats != null) {
            return stats.cpu;
        } else {
            // not started
            return 0;
        }
    }

    private Long mem(Stage.Current current) throws IOException {
        String container;
        Engine engine;
        Stats stats;

        container = current.container;
        if (container == null) {
            return null;
        }
        engine = server.session.dockerEngine();
        stats = engine.containerStats(container);
        if (stats != null) {
            return stats.memoryUsage * 100 / stats.memoryLimit;
        } else {
            // not started
            return 0L;
        }
    }
}
