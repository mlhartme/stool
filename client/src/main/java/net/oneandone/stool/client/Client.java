package net.oneandone.stool.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.common.BuildResult;
import net.oneandone.stool.common.Reference;
import net.oneandone.stool.server.docker.BuildError;
import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.docker.Stats;
import net.oneandone.stool.server.stage.Image;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.stool.server.util.LogEntry;
import net.oneandone.stool.server.util.LogReader;
import net.oneandone.stool.server.util.Ports;
import net.oneandone.stool.server.util.PredicateParser;
import net.oneandone.stool.server.util.Property;
import net.oneandone.stool.server.util.Session;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.HttpNode;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Client {
    private final Session session;

    public Client(Session session) {
        this.session = session;
    }

    //--

    /** @param stageClause null to return all stages */
    public List<Reference> search(String stageClause) throws IOException {
        List<Reference> result;

        result = new ArrayList<>();

        for (Stage stage : session.list(PredicateParser.parse(stageClause), new HashMap<>())) {
            result.add(stage.reference);
        }
        return result;
    }

    //-- create, build, start, stop, remove

    public Reference create(String name, Map<String, String> config) throws IOException {
        Stage stage;
        Property property;

        stage = session.create(name);
        for (Map.Entry<String, String> entry : config.entrySet()) {
            property = stage.propertyOpt(entry.getKey());
            if (property == null) {
                throw new ArgumentException("unknown property: " + entry.getKey());
            }
            property.set(entry.getValue());
        }
        stage.saveConfig();

        openStage(stage.reference);
        closeStage();
        return stage.reference;
    }

    public BuildResult build(Reference reference, String app, FileNode war, String comment,
                             String origin, String createdBy, String createdOn, boolean noCache, int keep,
                             Map<String, String> arguments) throws Exception {
        String output;

        openStage(reference);
        try {
            output = session.load(reference).build(app, war, comment, origin, createdBy, createdOn, noCache, keep, arguments);
            return new BuildResult(null, output);
        } catch (BuildError e) {
            return new BuildResult(e.error, e.output);
        } finally {
            closeStage();
        }
    }

    public void start(Reference reference, int http, int https, Map<String, String> startEnvironment, Map<String, Integer> apps) throws IOException {
        Stage stage;
        int global;
        int reserved;
        Map<String, String> environment;

        openStage(reference);
        try {
            environment = new HashMap<>(session.configuration.environment);
            environment.putAll(startEnvironment);
            global = session.configuration.quota;
            if (global != 0) {
                reserved = session.quotaReserved();
                if (reserved > global) {
                    throw new IOException("Sum of all stage quotas exceeds global limit: " + reserved + " mb > " + global + " mb.\n"
                            + "Use 'stool list name disk quota' to see actual disk usage vs configured quota.");
                }
            }

            stage = session.load(reference);
            stage.session.configuration.verfiyHostname();
            stage.checkConstraints();
            stage.start(http, https, environment, apps);
        } finally {
            closeStage();
        }
    }

    public Map<String, List<String>> awaitStartup(Reference reference) throws IOException, InterruptedException {
        Stage stage;
        Map<String, List<String>> result;

        stage = session.load(reference);
        stage.awaitStartup();

        result = new LinkedHashMap<>();
        for (String app : stage.currentMap().keySet()) {
            result.put(app, stage.namedUrls(app));
        }
        return result;
    }

    public void stop(Reference reference, List<String> apps) throws IOException {
        openStage(reference);
        try {
            session.load(reference).stop(apps);
        } finally {
            closeStage();
        }
    }

    public void remove(Reference reference) throws IOException {
        Stage stage;

        openStage(reference);
        try {
            stage = session.load(reference);
            stage.wipeDocker(session.dockerEngine());
            stage.getDirectory().deleteTree();
        } finally {
            closeStage();
        }
    }

    //--

    public Map<String, String> status(Reference reference, List<String> select) throws IOException {
        HttpNode node;
        JsonObject status;
        Map<String, String> result;

        node = node(reference, "status");
        node = node.withParameter("select", Separator.COMMA.join(select));
        status = httpGet(node).getAsJsonObject();
        result = new LinkedHashMap<>();
        for (String name : status.keySet()) {
            result.put(name, status.get(name).getAsString());
        }
        return result;
    }

    public List<String> history(Reference reference, boolean details, int max) throws IOException {
        String stageName;
        LogEntry entry;
        Map<String, List<LogEntry>> detailsMap; /* maps id to it's details */
        LogReader reader;
        List<LogEntry> lst;
        int counter;
        List<String> result;

        result = new ArrayList<>();
        stageName = reference.getName();
        counter = 0;
        detailsMap = new HashMap<>();
        reader = session.load(reference).logReader();
        while (true) {
            entry = reader.prev();
            if (entry == null) {
                break;
            }
            lst = detailsMap.get(entry.requestId);
            if (lst == null) {
                lst = new ArrayList<>();
                detailsMap.put(entry.requestId, lst);
            }
            if (entry.logger.equals("COMMAND")) {
                detailsMap.remove(entry.requestId);
                lst.add(entry);
                if (forStage(stageName, lst)) {
                    counter++;
                    result.add("[" + LogEntry.FULL_FMT.format(entry.dateTime) + " " + entry.user + "] " + entry.message);
                    if (details) {
                        for (int i = lst.size() - 1; i >= 0; i--) {
                            result.add(Strings.indent(lst.get(i).message, "     "));
                        }
                    }
                }
                if (counter == max) {
                    result.add("(skipping after " + max + " commands; use -max <n> to see more)");
                    break;
                }
            } else {
                lst.add(entry);
            }
        }
        return result;
    }

    private static boolean forStage(String stageName, List<LogEntry> lst) {
        for (LogEntry entry : lst) {
            if (stageName.equals(entry.stageName)) {
                return true;
            }
        }
        return false;
    }

    public String quota() throws IOException {
        String result;

        result = httpGet(node("quota")).getAsString();
        return result.isEmpty() ? null : result;
    }

    public int memUnreserved() throws IOException {
        String result;

        result = httpGet(node("memUnreserved")).getAsString();
        return Integer.parseInt(result);
    }

    public List<String> apps(Reference reference) throws IOException {
        List<String> result;

        result = new ArrayList<>(session.load(reference).images(session.dockerEngine()).keySet());
        Collections.sort(result);
        return result;
    }

    //-- validate

    public List<String> validate(String stageClause, boolean email, boolean repair) {
        return new ArrayList<>(); // TODO
    }

    //-- config command

    public Map<String, String> getProperties(Reference reference) throws Exception {
        Map<String, String> result;
        JsonObject properties;

        properties = httpGet(node(reference, "properties")).getAsJsonObject();
        result = new LinkedHashMap<>();
        for (String name : properties.keySet()) {
            result.put(name, properties.get(name).getAsString());
        }
        return result;
    }

    public Map<String, String> setProperties(Reference reference, Map<String, String> arguments) throws IOException {
        Stage stage;
        Property prop;
        String value;
        Map<String, String> result;

        result = new LinkedHashMap<>();
        stage = session.load(reference);
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            prop = stage.propertyOpt(entry.getKey());
            if (prop == null) {
                throw new ArgumentException("unknown property: " + entry.getKey());
            }
            value = entry.getValue();
            value = value.replace("{}", prop.get());
            try {
                prop.set(value);
                result.put(prop.name(), prop.getAsString());
            } catch (RuntimeException e) {
                throw new ArgumentException("invalid value for property " + prop.name() + " : " + e.getMessage());
            }
        }
        stage.saveConfig();
        return result;
    }

    //-- app info

    public List<String> appInfo(Reference reference, String app) throws Exception {
        Stage stage;
        Map<String, List<Image>> all;
        Map<String, Stage.Current> currentMap;
        Engine engine;
        String marker;
        int idx;
        Stage.Current current;
        Ports ports;
        List<String> result;

        result = new ArrayList<>();
        stage = session.load(reference);
        engine = stage.session.dockerEngine();
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
                url = stage.session.configuration.hostname + ":" + ports.jmxmp;
                result.add("                 jconsole " + url);
                result.add("                 jvisualvm --openjmx " + url);
            }
        }
        result.add("");
        for (Image image : all.get(app)) {
            marker = image.id.equals(current.image.id) ? "==>" : "   ";
            result.add(String.format("%s [%d] %s\n", marker, idx, image.id));
            result.add("       memory:     " + image.memory);
            result.add("       comment:    " + image.comment);
            result.add("       origin:     " + image.origin);
            result.add("       created-at: " + image.created);
            result.add("       created-by: " + image.createdBy);
            result.add("       created-on: " + image.createdOn);
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
            url = new JMXServiceURL("service:jmx:jmxmp://" + stage.session.configuration.hostname + ":" + stage.loadPorts().get(app).jmxmp);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        try {
            connection = JMXConnectorFactory.connect(url, null).getMBeanServerConnection();
        } catch (IOException e) {
            session.logging.verbose("cannot connect to jmx server", e);
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
        obj = session.dockerEngine().containerInspect(container, true);
        // not SizeRootFs, that's the image size plus the rw layer
        return (int) (obj.get("SizeRw").getAsLong() / (1024 * 1024));
    }

    private String uptime(Stage.Current current) throws IOException {
        String container;

        container = current.container;
        return container == null ? null : Stage.timespan(session.dockerEngine().containerStartedAt(container));
    }

    private Integer cpu(Stage.Current current) throws IOException {
        Engine engine;
        Stats stats;
        String container;

        container = current.container;
        if (container == null) {
            return null;
        }
        engine = session.dockerEngine();
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
        stats = session.dockerEngine().containerStats(container);
        if (stats != null) {
            return stats.memoryUsage * 100 / stats.memoryLimit;
        } else {
            // not started
            return 0L;
        }
    }

    private void openStage(Reference reference) throws MkdirException {
        session.logging.openStage(reference.getName());
        session.logging.command(session.command);
    }

    private void closeStage() {
        session.logging.closeStage();
    }

    //--

    private HttpNode node(Reference reference, String cmd) throws IOException {
        return node("stage/" + reference.getName() + "/" + cmd);
    }

    private HttpNode node(String path) throws IOException {
        return (HttpNode) session.world.validNode("http://localhost:8080/").join(path);
    }

    private JsonElement httpGet(HttpNode node) throws IOException {
        String response;

        response = node.readString();
        //System.out.println("path: " + path);
        //System.out.println("response: " + response);
        return new JsonParser().parse(response);
    }
}
