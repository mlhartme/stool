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
import io.fabric8.kubernetes.client.KubernetesClientException;
import net.oneandone.stool.kubernetes.OpenShift;
import net.oneandone.stool.server.api.StageNotFoundException;
import net.oneandone.stool.server.settings.Expire;
import net.oneandone.stool.server.settings.Settings;
import net.oneandone.stool.server.settings.adapter.ExpireTypeAdapter;
import net.oneandone.stool.server.settings.adapter.FileNodeTypeAdapter;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.server.logging.AccessLogEntry;
import net.oneandone.stool.server.logging.DetailsLogEntry;
import net.oneandone.stool.server.logging.LogReader;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.stool.server.users.UserManager;
import net.oneandone.stool.server.util.Predicate;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
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
    public static final Map<String, String> STOOL_LABELS = Strings.toMap("origin", "net.oneandone.stool.server");

    public static final Logger LOGGER = LoggerFactory.getLogger("DETAILS");

    public static Server create(World world) throws IOException {
        String version;
        Settings settings;
        Server server;
        String localhostIp;
        version = Main.versionString(world);
        boolean openShift;

        settings = Settings.load();
        LOGGER.info("server version: " + Main.versionString(world));
        LOGGER.info("server auth: " + settings.auth());
        LOGGER.info("server settings: " + settings);
        try (Engine engine = Engine.createFromCluster(STOOL_LABELS)) {
            localhostIp = InetAddress.getByName("localhost").getHostAddress();
            LOGGER.info("localhostIp: " + localhostIp);
            try (OpenShift os = OpenShift.create()) {
                try {
                    os.routeList();
                    openShift = true;
                } catch (KubernetesClientException e) {
                    if (e.getCode() == 404) {
                        openShift = false;
                    } else {
                        throw e;
                    }
                }
            }
            LOGGER.info("OpenShift: " + openShift);
            server = new Server(gson(world), version, world, openShift, localhostIp, settings);
            server.validate(engine);
            return server;
        }
    }

    //--

    /** gson is thread-save */
    public final Gson gson;

    public final String version;

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

    public Server(Gson gson, String version, World world, boolean openShift, String localhostIp, Settings settings) throws IOException {
        this.gson = gson;
        this.version = version;
        this.world = world;
        this.openShift = openShift;
        this.home = world.file("/var/lib/stool").checkDirectory();
        this.serverLogs = world.file("/var/log/stool");
        this.stageLogs = world.file("/var/log/stages");
        this.localhostIp = localhostIp;
        this.settings = settings;
        this.userManager = UserManager.loadOpt(home.join("users.json"));
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
        return new Stage(this, name);
    }

    //--

    /** logs an error for administrators, i.e. the user is not expected to understand/fix this problem. */
    public void reportException(String command, String context, Throwable e) {
        String subject;
        StringWriter body;
        PrintWriter writer;

        LOGGER.error("[" + command + "] " + context + ": " + e.getMessage(), e);
        if (!settings.admin.isEmpty()) {
            subject = "[stool exception] " + e.getMessage();
            body = new StringWriter();
            body.write("stool: " + Main.versionString(world) + "\n");
            body.write("command: " + command + "\n");
            body.write("context: " + context + "\n");
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

    public void validate(Engine engine) throws IOException {
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
        try {
            LOGGER.info("kubernetes info: " + engine.version());
        } catch (IOException e) {
            LOGGER.error("cannot access kubernetes", e);
            throw e;
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
