package net.oneandone.stool.server.util;

import com.google.gson.JsonObject;
import net.oneandone.stool.server.Server;
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
        result.add("cpu:       " + cpu(current));
        result.add("mem:       " + mem(current));
        result.add("container: " + current.container);
        result.add("origin:    " + current.image.origin);
        result.add("uptime:    " + uptime(current));
        result.add("heap:      " + heap(stage, app, current));
        result.add("disk-used: " + diskUsed(current));
        if (ports != null) {
            if (ports.debug != -1) {
                result.add("debug port " + ports.debug);
            }
            if (ports.jmxmp != -1) {
                String url;

                result.add("jmx port:  " + ports.jmxmp);
                url = stage.server.configuration.dockerHost + ":" + ports.jmxmp;
                result.add("                 jconsole " + url);
                result.add("                 jvisualvm --openjmx " + url);
            }
        }
        result.add("");
        for (Image image : all.get(app)) {
            marker = image.tag.equals(current.image.tag) ? "==>" : "   ";
            result.add(String.format("%s [%d] %s\n", marker, idx, image.tag));
            result.add("       memory:     " + image.memory);
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

    public int diskUsed(Stage.Current current) throws IOException {
        String container;
        JsonObject obj;

        container = current.container;
        if (container == null) {
            return 0;
        }
        obj = context.dockerEngine().containerInspect(container, true);
        // not SizeRootFs, that's the image size plus the rw layer
        return (int) (obj.get("SizeRw").getAsLong() / (1024 * 1024));
    }

    private String uptime(Stage.Current current) throws IOException {
        String container;

        container = current.container;
        return container == null ? null : Stage.timespan(context.dockerEngine().containerStartedAt(container));
    }

    private Integer cpu(Stage.Current current) throws IOException {
        Engine engine;
        Stats stats;
        String container;

        container = current.container;
        if (container == null) {
            return null;
        }
        engine = context.dockerEngine();
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
        Stats stats;

        container = current.container;
        if (container == null) {
            return null;
        }
        stats = context.dockerEngine().containerStats(container);
        if (stats != null) {
            return stats.memoryUsage * 100 / stats.memoryLimit;
        } else {
            // not started
            return 0L;
        }
    }

}
