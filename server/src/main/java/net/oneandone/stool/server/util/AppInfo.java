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
package net.oneandone.stool.server.util;

import com.google.gson.JsonObject;
import net.oneandone.stool.server.Server;
import net.oneandone.stool.server.docker.ContainerInfo;
import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.docker.Stats;
import net.oneandone.stool.server.stage.Image;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.sushi.util.Separator;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppInfo {
    private final Server context;
    private final Engine engine;

    public AppInfo(Server context, Engine engine) {
        this.context = context;
        this.engine = engine;
    }

    public List<String> run(String name, String app) throws Exception {
        Stage stage;
        Map<String, List<Image>> all;
        Map<String, Stage.Current> currentMap;
        String marker;
        Stage.Current current;
        Ports ports;
        List<String> result;
        List<String> args;

        result = new ArrayList<>();
        stage = context.load(name);
        all = stage.images(engine);
        currentMap = stage.currentMap(engine);
        for (Map.Entry<String, List<Image>> entry : all.entrySet()) {
            if (!currentMap.containsKey(entry.getKey())) {
                currentMap.put(entry.getKey(), new Stage.Current(entry.getValue().get(entry.getValue().size() - 1), null));
            }
        }
        current = currentMap.get(app);
        for (Image image : all.get(app)) {
            marker = image.repositoryTag.equals(current.image.repositoryTag) ? "<==" : "";
            result.add(app + ":" + image.tag + "  " + marker);
            result.add("   comment:     " + image.comment);
            result.add("   origin-scm:  " + image.originScm);
            result.add("   origin-user: " + image.originUser);
            result.add("   created-at:  " + image.createdAt);
            result.add("   created-by:  " + image.createdBy);
            result.add("   memory:      " + image.memory);
            result.add("   disk:        " + image.disk);
            result.add("   build args:");
            args = new ArrayList<>(image.args.keySet());
            Collections.sort(args);
            for (String arg : args) {
                result.add("       " + arg + ": \t" + image.args.get(arg));
            }
            result.add("   secrets:    " + Separator.COMMA.join(image.faultProjects));
        }
        result.add("container:  " + (current.container == null ? "" : current.container.id));
        result.add("uptime:     " + uptime(current.container));
        result.add("disk-used:  " + diskUsed(engine, current.container));
        result.add("cpu:        " + cpu(current.container));
        result.add("mem:        " + mem(current.container));
        result.add("heap:       " + heap(stage, app, current));
        addEnv(current.container, result);
        result.add("origin-scm: " + current.image.originScm);
        ports = stage.loadPorts(engine).get(app);
        if (ports != null) {
            if (ports.debug != -1) {
                result.add("debug port: " + ports.debug);
            }
            if (ports.jmxmp != -1) {
                result.add("jmx port:   " + ports.jmxmp);
                result.add("                 " + String.format(stage.server.configuration.jmxUsage, ports.jmxmp));
            }
        }
        return result;
    }

    private void addEnv(ContainerInfo info, List<String> result) {
        Map<String, String> env;
        List<String> keys;

        result.add("environment:");
        env = env(info);
        keys = new ArrayList<>(env.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            result.add("    " + key + ": \t" + env.get(key));
        }
    }

    public String heap(Stage stage, String app, Stage.Current current) throws IOException {
        JMXServiceURL url;
        MBeanServerConnection connection;
        ObjectName name;
        CompositeData result;
        long used;
        long max;

        if (current.container == null) {
            return "";
        }
        if (current.image.ports.jmxmp == -1) {
            return "[no jmx port]";
        }

        url = stage.jmxMap(engine).get(app);
        try {
            name = new ObjectName("java.lang:type=Memory");
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException(e);
        }
        try (JMXConnector raw = JMXConnectorFactory.connect(url, null)) {
            connection = raw.getMBeanServerConnection();
            try {
                result = (CompositeData) connection.getAttribute(name, "HeapMemoryUsage");
            } catch (Exception e) {
                return "[cannot get jmx attribute: " + e.getMessage() + "]";
            }
        } catch (IOException e) {
            Server.LOGGER.debug("cannot connect to jmx server", e);
            return "[cannot connect jmx server: " + e.getMessage() + "]";
        }
        used = (Long) result.get("used");
        max = (Long) result.get("max");
        return Float.toString(((float) (used * 1000 / max)) / 10);
    }

    public static int diskUsed(Engine engine, ContainerInfo info) throws IOException {
        JsonObject obj;

        if (info == null) {
            return 0;
        }
        obj = engine.containerInspect(info.id, true);
        // not SizeRootFs, that's the image size plus the rw layer
        return (int) (obj.get("SizeRw").getAsLong() / (1024 * 1024));
    }

    private Map<String, String> env(ContainerInfo info) {
        Map<String, String> result;
        String key;

        result = new HashMap<>();
        if (info != null) {
            for (Map.Entry<String, String> entry : info.labels.entrySet()) {
                key = entry.getKey();
                if (key.startsWith(Stage.CONTAINER_LABEL_ENV_PREFIX)) {
                    result.put(key.substring(Stage.CONTAINER_LABEL_ENV_PREFIX.length()), entry.getValue());
                }
            }
        }
        return result;
    }

    private String uptime(ContainerInfo info) throws IOException {
        return info == null ? null : Stage.timespan(engine.containerStartedAt(info.id));
    }

    private Integer cpu(ContainerInfo info) throws IOException {
        Stats stats;

        if (info == null) {
            return null;
        }
        stats = engine.containerStats(info.id);
        if (stats != null) {
            return stats.cpu;
        } else {
            // not started
            return 0;
        }
    }

    private Long mem(ContainerInfo info) throws IOException {
        Stats stats;

        if (info == null) {
            return null;
        }
        stats = engine.containerStats(info.id);
        if (stats != null) {
            return stats.memoryUsage * 100 / stats.memoryLimit;
        } else {
            // not started
            return 0L;
        }
    }

}
