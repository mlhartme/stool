package net.oneandone.stool.util;

import com.google.gson.JsonObject;
import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.stool.cli.EnumerationFailed;
import net.oneandone.stool.cli.Remove;
import net.oneandone.stool.cli.Stop;
import net.oneandone.stool.cli.Validate;
import net.oneandone.stool.docker.Engine;
import net.oneandone.stool.docker.Stats;
import net.oneandone.stool.stage.Image;
import net.oneandone.stool.stage.Reference;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.stage.State;
import net.oneandone.stool.users.User;
import net.oneandone.stool.users.UserNotFound;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import javax.mail.MessagingException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.naming.NamingException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Server {
    public final Console console;
    public final World world;
    private final Session session;

    public Server(Session session) {
        this.console = session.console;
        this.world = session.world;
        this.session = session;
    }

    public String getName(Reference reference) throws IOException {
        return session.load(reference).getName();
    }

    public Reference resolveName(String name) throws IOException {
        return session.loadByName(name).reference;
    }

    public List<Reference> list(EnumerationFailed problems, Predicate predicate) throws IOException {
        List<Reference> result;

        result = new ArrayList<>();
        for (Stage stage : session.list(problems, predicate)) {
            result.add(stage.reference);
        }
        return result;
    }

    public List<Info> status(Reference reference, List<String> selected) throws IOException {
        Stage stage;
        List<Info> result;

        stage = session.load(reference);
        if (selected.isEmpty()) {
            if (selected.isEmpty()) {
                for (Info info : stage.fieldsAndName()) {
                    selected.add(info.name());
                }
            }
        }
        result = new ArrayList<>();
        for (String name : selected) {
            result.add(stage.info(name));
        }
        return result;
    }

    public List<String> history(Reference reference, boolean details, int max) throws IOException {
        String stageId;
        LogEntry entry;
        Map<String, List<LogEntry>> detailsMap; /* id to it's details */
        LogReader reader;
        List<LogEntry> lst;
        int counter;
        List<String> result;

        result = new ArrayList<>();
        stageId = reference.getId();
        counter = 0;
        detailsMap = new HashMap<>();
        reader = session.load(reference).logReader();
        while (true) {
            entry = reader.prev();
            if (entry == null) {
                break;
            }
            lst = detailsMap.get(entry.id);
            if (lst == null) {
                lst = new ArrayList<>();
                detailsMap.put(entry.id, lst);
            }
            if (entry.logger.equals("COMMAND")) {
                detailsMap.remove(entry.id);
                if (forStage(stageId, lst)) {
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

    private static boolean forStage(String stageId, List<LogEntry> lst) {
        for (LogEntry entry : lst) {
            if (stageId.equals(entry.stageId)) {
                return true;
            }
        }
        return false;
    }

    public void rotateLogs(Reference reference) throws IOException {
        session.load(reference).rotateLogs(session.console);
    }

    public void remove(Reference reference) throws IOException {
        Stage stage;

        stage = session.load(reference);
        stage.wipeDocker(session.dockerEngine());
        stage.getDirectory().deleteTree();
    }

    public String quota() throws IOException {
        int global;

        global = session.configuration.quota;
        if (global == 0) {
            return null;
        } else {
            return session.quotaReserved() + "/" + global;
        }
    }

    public int memUnreserved() throws IOException {
        return session.memUnreserved();
    }

    public State state(Reference reference) throws IOException {
        return session.load(reference).state();
    }

    public List<String> apps(Reference reference) throws IOException {
        List<String> result;

        result = new ArrayList<>(session.load(reference).images(session.dockerEngine()).keySet());
        Collections.sort(result);
        return result;
    }

    public void start(Reference reference, int http, int https, Map<String, String> startEnvironment, Map<String, Integer> apps) throws IOException {
        Stage stage;
        int global;
        int reserved;
        Map<String, String> environment;

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
        stage.start(session.console,  http, https, environment, apps);
    }

    public Map<String, List<String>> awaitStartup(Reference reference) throws IOException, InterruptedException {
        Stage stage;
        Map<String, List<String>> result;

        stage = session.load(reference);
        stage.awaitStartup(session.console);

        result = new LinkedHashMap<>();
        for (String app : stage.currentMap().keySet()) {
            stage.namedUrls(app);
        }
        return result;
    }

    public void stop(Reference reference, List<String> apps) throws IOException {
        session.load(reference).stop(session.console, apps);
    }

    public void create(Project backstage, FileNode project, Map<String, String> config, Console console) throws IOException {
        Stage stage;
        Property property;

        stage = session.create(backstage.getOrigin());
        backstage.setAttached(stage.reference);
        stage.configuration.name = project.getName();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            property = stage.propertyOpt(entry.getKey());
            if (property == null) {
                throw new ArgumentException("unknown property: " + entry.getKey());
            }
            property.set(entry.getValue());
        }
        Project.checkName(stage.configuration.name);
        stage.saveConfig();

        session.logging.openStage(stage.reference.getId(), stage.getName());
        console.info.println("stage create: " + stage.getName());
        session.logging.closeStage();
    }


    public void build(Reference reference, Project project, String app, FileNode war, Console console, String comment,
                      String origin, String createdBy, String createdOn, boolean noCache, int keep) throws Exception {
        session.load(reference).build(project, app, war, console, comment, origin, createdBy, createdOn, noCache, keep);
    }

    //-- validate

    public void email(Validate.Report report) throws MessagingException, NamingException {
        String hostname;
        Mailer mailer;
        Console console;
        String user;
        String email;
        String body;

        hostname = session.configuration.hostname;
        mailer = session.configuration.mailer();
        console = session.console;
        for (Map.Entry<String, List<String>> entry : report.users.entrySet()) {
            user = entry.getKey();
            body = Separator.RAW_LINE.join(entry.getValue());
            email = email(session, user);
            if (email == null) {
                console.error.println("cannot send email, there's nobody to send it to.");
            } else {
                console.info.println("sending email to " + email);
                mailer.send("stool@" + hostname, new String[] { email }, "Validation of your stage(s) on " + hostname + " failed", body);
            }
        }
    }

    private static String email(Session session, String user) throws NamingException {
        User userobj;
        String email;

        if (user == null) {
            email = session.configuration.admin;
        } else {
            if (user.contains("@")) {
                return user;
            }
            try {
                userobj = session.users.byLogin(user);
                email = (userobj.isGenerated() ? session.configuration.admin : userobj.email);
            } catch (UserNotFound e) {
                email = session.configuration.admin;
            }
        }
        return email.isEmpty() ? null : email;
    }


    public void validateState(Reference reference, Validate.Report report, boolean repair) throws IOException {
        Stage stage;
        String message;

        stage = session.load(reference);
        try {
            stage.checkConstraints();
            return;
        } catch (ArgumentException e) {
            message = e.getMessage();
        }
        report.user(stage, message);
        if (repair) {
            if (!stage.dockerContainerList().isEmpty()) {
                try {
                    new Stop(this).doRun(stage.reference);
                    report.user(stage, "stage has been stopped");
                } catch (Exception e) {
                    report.user(stage, "stage failed to stop: " + e.getMessage());
                    e.printStackTrace(session.console.verbose);
                }
            }
            if (session.configuration.autoRemove >= 0 && stage.configuration.expire.expiredDays() >= 0) {
                if (stage.configuration.expire.expiredDays() >= session.configuration.autoRemove) {
                    try {
                        report.user(stage, "removing expired stage");
                        new Remove(this, true, true).doRun(stage.reference);
                    } catch (Exception e) {
                        report.user(stage, "failed to remove expired stage: " + e.getMessage());
                        e.printStackTrace(session.console.verbose);
                    }
                } else {
                    report.user(stage, "CAUTION: This stage will be removed automatically in "
                            + (session.configuration.autoRemove - stage.configuration.expire.expiredDays()) + " day(s)");
                }
            }
        }
    }


    public void validateServer(Validate.Report report) throws IOException {
        validateDocker(report);
        validateDns(report);
    }

    private void validateDocker(Validate.Report report) {
        try {
            session.dockerEngine().imageList(Collections.emptyMap());
        } catch (IOException e) {
            report.admin("cannot access docker: " + e.getMessage());
            e.printStackTrace(session.console.verbose);
        }
    }

    private void validateDns(Validate.Report report) throws IOException {
        int port;
        String ip;
        String subDomain;
        ServerSocket socket;

        try {
            ip = digIp(session.configuration.hostname);
        } catch (Failure e) {
            report.admin("cannot validate dns entries: " + e.getMessage());
            return;
        }
        if (ip.isEmpty()) {
            report.admin("missing dns entry for " + session.configuration.hostname);
            return;
        }

        // make sure that hostname points to this machine. Help to detect actually adding the name of a different machine
        port = session.pool().temp();
        try {
            socket = new ServerSocket(port,50, InetAddress.getByName(session.configuration.hostname));
            socket.close();
        } catch (IOException e) {
            report.admin("cannot open socket on machine " + session.configuration.hostname + ", port " + port + ". Check the configured hostname.");
            e.printStackTrace(session.console.verbose);
        }

        subDomain = digIp("foo." + session.configuration.hostname);
        if (subDomain.isEmpty() || !subDomain.endsWith(ip)) {
            report.admin("missing dns * entry for " + session.configuration.hostname + " (" + subDomain + ")");
        }
    }

    private String digIp(String name) throws Failure {
        Launcher dig;

        dig = new Launcher(session.world.getWorking(), "dig", "+short", name);
        return dig.exec().trim();
    }

    //--

    /** CAUTION: do not place this in a session, because it doesn't work long-lived sessions (dashboard!) */
    private Processes lazyProcesses = null;

    public Processes processes() throws Failure {
        if (lazyProcesses == null) {
            lazyProcesses = Processes.load(session.world);
        }
        return lazyProcesses;
    }

    //-- config command

    public List<Property> getProperties(Reference reference) throws Exception {
        return session.load(reference).properties();
    }

    public List<Property> setProperties(Reference reference, Map<String, String> arguments) throws IOException {
        Stage stage;
        Property prop;
        String value;
        List<Property> result;

        result = new ArrayList<>();
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
                result.add(prop);
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
            result.add("       comment:    " + image.comment);
            result.add("       origin:     " + image.origin);
            result.add("       created-at: " + image.created);
            result.add("       created-by: " + image.createdBy);
            result.add("       created-on: " + image.createdOn);
            result.add("       secrets:    " + Separator.SPACE.join(image.secrets.keySet()));
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

    public void openStage(Reference reference, String name) throws MkdirException {
        session.logging.openStage(reference.getId(), name);

    }

    public void closeStage() {
        session.logging.closeStage();
    }
}
