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
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppInfo {
    private final Server context;

    public AppInfo(Server context) {
        this.context = context;
    }

    public List<String> run(String name, String app) throws Exception {
        Stage stage;
        Map<String, List<Image>> all;
        Map<String, Stage.Current> currentMap;
        Engine engine;
        String marker;
        int idx;
        Stage.Current current;
        Ports ports;
        List<String> result;
        List<String> args;

        result = new ArrayList<>();
        stage = context.load(name);
        engine = stage.server.dockerEngine();
        all = stage.images(engine);
        currentMap = stage.currentMap();
        for (Map.Entry<String, List<Image>> entry : all.entrySet()) {
            if (!currentMap.containsKey(entry.getKey())) {
                currentMap.put(entry.getKey(), new Stage.Current(entry.getValue().get(0), null));
            }
        }

        current = currentMap.get(app);
        idx = 0;
        ports = stage.loadPorts().get(app);
        result.add("app:       " + app);
        result.add("container: " + (current.container == null ? "" : current.container.id));
        result.add("uptime:    " + uptime(current.container));
        result.add("disk-used: " + diskUsed(engine, current.container));
        result.add("cpu:       " + cpu(current.container));
        result.add("mem:       " + mem(current.container));
        result.add("heap:      " + heap(stage, app, current));
        addEnv(current.container, result);
        result.add("origin:    " + current.image.origin);
        if (ports != null) {
            if (ports.debug != -1) {
                result.add("debug port " + ports.debug);
            }
            if (ports.jmxmp != -1) {
                result.add("jmx port:  " + ports.jmxmp);
                result.add("                 " + String.format(stage.server.configuration.jmxUsage, stage.server.configuration.dockerHost + ":" + ports.jmxmp));
            }
        }
        result.add("");
        for (Image image : all.get(app)) {
            marker = image.tag.equals(current.image.tag) ? "==>" : "   ";
            result.add(String.format("%s [%d] %s\n", marker, idx, image.tag));
            result.add("       memory:     " + image.memory);
            result.add("       disk:       " + image.disk);
            result.add("       comment:    " + image.comment);
            result.add("       origin:     " + image.origin);
            result.add("       created-at: " + image.created);
            result.add("       created-by: " + image.createdBy);
            result.add("       created-on: " + image.createdOn);
            result.add("       build args:");
            args = new ArrayList<>(image.args.keySet());
            Collections.sort(args);
            for (String arg : args) {
                result.add("           " + arg + ": \t" + image.args.get(arg));
            }
            result.add("       secrets:    " + Separator.COMMA.join(image.faultProjects));
            idx++;
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

        // see https://docs.oracle.com/javase/tutorial/jmx/remote/custom.html
        try {
            url = new JMXServiceURL("service:jmx:jmxmp://" + stage.server.configuration.dockerHost + ":" + stage.loadPorts().get(app).jmxmp);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        try {
            connection = JMXConnectorFactory.connect(url, null).getMBeanServerConnection();
        } catch (IOException e) {
            Server.LOGGER.debug("cannot connect to jmx server", e);
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
        return info == null ? null : Stage.timespan(context.dockerEngine().containerStartedAt(info.id));
    }

    private Integer cpu(ContainerInfo info) throws IOException {
        Engine engine;
        Stats stats;

        if (info == null) {
            return null;
        }
        engine = context.dockerEngine();
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
        stats = context.dockerEngine().containerStats(info.id);
        if (stats != null) {
            return stats.memoryUsage * 100 / stats.memoryLimit;
        } else {
            // not started
            return 0L;
        }
    }

}
