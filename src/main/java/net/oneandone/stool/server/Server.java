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
package net.oneandone.stool.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.oneandone.stool.client.Configuration;
import net.oneandone.stool.registry.PortusRegistry;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.server.api.StageNotFoundException;
import net.oneandone.stool.server.settings.Expire;
import net.oneandone.stool.server.settings.Settings;
import net.oneandone.stool.server.settings.adapter.ExpireTypeAdapter;
import net.oneandone.stool.server.settings.adapter.FileNodeTypeAdapter;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.server.logging.AccessLogEntry;
import net.oneandone.stool.server.logging.DetailsLogEntry;
import net.oneandone.stool.server.logging.LogReader;
import net.oneandone.stool.server.users.UserManager;
import net.oneandone.stool.server.util.Predicate;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.mail.MessagingException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Immutable. */
public class Server {
    public static final Logger LOGGER = LoggerFactory.getLogger("DETAILS");

    public static Server createLocal(World world, String context) throws IOException {
        Server result;
        Configuration client;

        result = create(world, context);
        client = new Configuration(world);
        client.load(world.getHome().join(".sc.yaml"));
        result.settings.setRegistryCredentials(client.registryCredentials);
        return result;
    }

    public static Server createCluster(World world) throws IOException {
        return create(world, null);
    }

    private static Server create(World world, String context) throws IOException {
        String version;
        Settings settings;
        Server server;
        String localhostIp;
        version = Main.versionString(world);
        boolean openShift;
        String charts;
        FileNode home;
        FileNode logbase;

        settings = Settings.load();
        LOGGER.info("server version: " + Main.versionString(world));
        LOGGER.info("context: " + context);
        LOGGER.info("server auth: " + settings.auth());
        LOGGER.info("server settings: " + settings);
        try (Engine engine = Engine.createClusterOrLocal(context)) {
            openShift = engine.isOpenShift();
            LOGGER.info("OpenShift: " + openShift);
            localhostIp = InetAddress.getByName("localhost").getHostAddress();
            LOGGER.info("localhostIp: " + localhostIp);
            if (context == null) {
                charts = "/etc/charts";
                home = world.file("/var/lib/stool");
                logbase = world.file("/var/log");
            } else {
                charts = "/Users/mhm/Projects/helmcharts";
                home = world.getHome().join(".sc").mkdirOpt(); // TODO
                logbase = world.getHome().join(".sc-logs").mkdirOpt(); // TODO
            }
            server = new Server(charts, gson(world), version, context, home, logbase, world, openShift, localhostIp, settings);
            server.validate();
            return server;
        }
    }

    //--

    public final String charts;

    /** gson is thread-save */
    public final Gson gson;

    public final String version;

    public final String context;

    /** CAUTION: not thread safe! Try to use engine.world instead */
    private final World world;

    public final boolean openShift;

    /** CAUTION: not thread safe! */
    private final FileNode home;

    /** Logs of the server. CAUTION: not thread safe! */
    private final FileNode serverLogs;

    /** Logs of stages. CAUTION: not thread safe! */
    private final FileNode stageLogs;

    public final String localhostIp;

    /** used read-only */
    public final Settings settings;

    public final UserManager userManager;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public Server(String charts, Gson gson, String version, String context, FileNode home, FileNode logbase, World world,
                  boolean openShift, String localhostIp, Settings settings) throws IOException {
        this.charts = charts;
        this.gson = gson;
        this.version = version;
        this.context = context;
        this.world = world;
        this.openShift = openShift;
        this.home = home.checkDirectory();
        this.serverLogs = logbase.join("server");
        this.stageLogs = logbase.join("stages");
        this.localhostIp = localhostIp;
        this.settings = settings;
        this.userManager = UserManager.loadOpt(home.join("users.json"));
    }

    public String stageFqdn(String stage) {
        return stage + "." + settings.fqdn;
    }

    public FileNode getServerLogs() {
        return serverLogs;
    }

    public FileNode getStageLogs(String name) {
        return stageLogs.join(name);
    }

    /** @return last entry first; list may be empty because old log files are removed. */
    public List<AccessLogEntry> accessLog(String stage, int max, boolean modificationsOnly) throws IOException {
        AccessLogEntry entry;
        List<AccessLogEntry> entries;
        LogReader<AccessLogEntry> reader;
        String previousInvocation;

        entries = new ArrayList<>();
        reader = LogReader.accessLog(serverLogs);
        while (true) {
            entry = reader.prev();
            if (entry == null) {
                break;
            }
            if (stage.equals(entry.stageName)) {
                if (!modificationsOnly || (modificationsOnly && entry.request.startsWith("POST "))) {
                    previousInvocation = entries.isEmpty() ? "" : entries.get(entries.size() - 1).clientInvocation;
                    if (!entry.clientInvocation.equals(previousInvocation)) {
                        entries.add(entry);
                    }
                    if (entries.size() == max) {
                        break;
                    }
                }
            }
        }
        return entries;
    }

    public List<DetailsLogEntry> detailsLog(String clientInvocation) throws IOException {
        DetailsLogEntry entry;
        List<DetailsLogEntry> entries;
        LogReader<DetailsLogEntry> reader;

        entries = new ArrayList<>();
        reader = LogReader.detailsLog(serverLogs);
        while (true) {
            entry = reader.prev();
            if (entry == null) {
                break;
            }
            if (clientInvocation.equals(entry.clientInvocation)) {
                entries.add(entry);
            }
        }
        return entries;
    }

    //-- Stage listings

    public List<Stage> list(Engine engine, Predicate predicate, Map<String, IOException> problems) throws IOException {
        List<Stage> result;
        Stage stage;

        result = new ArrayList<>();
        for (String name : list(engine)) {
            try {
                stage = load(engine, name);
            } catch (IOException e) {
                e.printStackTrace();
                problems.put(name, e);
                continue;
            }
            if (predicate.matches(stage)) {
                result.add(stage);
            }
        }
        return result;
    }

    private static List<String> list(Engine engine) throws IOException {
        List<String> result;

        result = engine.helmList();
        if (!result.remove("stool")) {
            throw new IllegalStateException(result.toString());
        }
        return result;
    }

    public List<Stage> listAll(Engine engine) throws IOException {
        List<Stage> result;
        Map<String, IOException> problems;

        problems = new HashMap<>();
        result = list(engine, new Predicate() {
            @Override
            public boolean matches(Stage stage) {
                return true;
            }
        }, problems);
        for (Map.Entry<String, IOException> entry : problems.entrySet()) {
            reportException("listAll" /* TODO */, entry.getKey() + ": Session.listAll", entry.getValue());
        }
        return result;
    }

    //-- Stage access

    public Stage load(Engine engine, String name) throws IOException {
        if (!list(engine).contains(name)) {
            throw new StageNotFoundException(name);
        }
        return Stage.create(this, name, engine.helmRead(name));
    }

    //--

    public Registry createRegistry(World registryWorld, String image) throws IOException {
        int idx;
        String host;
        Settings.UsernamePassword up;
        String uri;

        idx = image.indexOf('/');
        if (idx == -1) {
            throw new IllegalArgumentException(image);
        }
        host = image.substring(0, idx);
        uri = "https://";
        up = settings.registryCredentials(host);
        if (up != null) {
            uri = uri + up.username + ":" + up.password + "@";
        }
        uri = uri + host;
        return PortusRegistry.create(registryWorld, uri, null);
    }

    //--

    /** logs an error for administrators, i.e. the user is not expected to understand/fix this problem. */
    public void reportException(String command, String exceptionContext, Throwable e) {
        String subject;
        StringWriter body;
        PrintWriter writer;

        LOGGER.error("[" + command + "] " + exceptionContext + ": " + e.getMessage(), e);
        if (!settings.admin.isEmpty()) {
            subject = "[stool exception] " + e.getMessage();
            body = new StringWriter();
            body.write("stool: " + Main.versionString(world) + "\n");
            body.write("command: " + command + "\n");
            body.write("context: " + exceptionContext + "\n");
            body.write("user: " + MDC.get("USER") + "\n"); // TODO
            body.write("fqdn: " + settings.fqdn + "\n");
            writer = new PrintWriter(body);
            while (true) {
                e.printStackTrace(writer);
                e = e.getCause();
                if (e == null) {
                    break;
                }
                body.append("Caused by:\n");
            }
            try {
                settings.mailer().send(settings.admin, new String[]{settings.admin}, subject, body.toString());
            } catch (MessagingException suppressed) {
                LOGGER.error("cannot send exception email: " + suppressed.getMessage(), suppressed);
            }
        }
    }

    //-- stool properties

    public static Gson gson(World world) {
        return new GsonBuilder()
                .registerTypeAdapter(FileNode.class, new FileNodeTypeAdapter(world))
                .registerTypeAdapter(Expire.class, new ExpireTypeAdapter())
                .disableHtmlEscaping()
                .serializeNulls()
                .excludeFieldsWithModifiers(Modifier.STATIC, Modifier.TRANSIENT)
                .setPrettyPrinting()
                .create();
    }

    public static String majorMinor(String version) {
        int major;
        int minor;

        major = version.indexOf('.');
        minor = version.indexOf('.', major + 1);
        if (minor == -1) {
            throw new IllegalArgumentException(version);
        }
        return version.substring(0, minor);
    }

    //--

    /** @return path to generates directory */
    public FileNode certificate(String certname) throws IOException {
        FileNode script;
        FileNode dir;
        FileNode broken;

        script = home.join("cert.sh");
        if (!script.isFile()) {
            throw new IOException("don't know how to generate certificate: " + script.getAbsolute());
        }
        dir = home.join("certs", certname);
        try {
            LOGGER.debug(world.getTemp().exec(script.getAbsolute(), certname, dir.getAbsolute(), settings.fqdn));
        } catch (IOException e) {
            broken = dir.getParent().join(dir.getName() + ".broken");
            broken.deleteTreeOpt();
            dir.move(broken);
            broken.join("error.log").writeString(e.getMessage());
            throw e;
        }
        return dir;
    }

    //--

    public void validate() throws IOException {
        if (settings.auth()) {
            if (settings.ldapSso.isEmpty()) {
                LOGGER.error("ldapSso cannot be empty because security is enabled");
                throw new IOException("ldapSso is empty");
            }
            if (System.getProperty("server.ssl.key-store") == null) {
                LOGGER.error("enable ssl when running authenticated");
                throw new IOException("enable ssl when running authenticated");
            }
        }
        LOGGER.info("server validation ok");
    }

    public void checkFaultPermissions(String user, List<String> projects) throws IOException {
        Properties permissions;
        String lst;

        if (projects.isEmpty()) {
            return;
        }
        permissions = world.file("/etc/fault/workspace.permissions").readProperties();
        for (String project : projects) {
            lst = permissions.getProperty(project);
            if (lst == null) {
                throw new ArgumentException("fault project unknown or not accessible on this host: " + project);
            }
            if (!Separator.COMMA.split(lst).contains(user)) {
                throw new ArgumentException("fault project " + project + ": permission denied for user " + user);
            }
        }
    }
}
