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
import net.oneandone.stool.registry.PortusRegistry;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.server.api.StageNotFoundException;
import net.oneandone.stool.server.configuration.Accessor;
import net.oneandone.stool.server.configuration.Expire;
import net.oneandone.stool.server.configuration.ServerConfiguration;
import net.oneandone.stool.server.configuration.StageConfiguration;
import net.oneandone.stool.server.configuration.adapter.ExpireTypeAdapter;
import net.oneandone.stool.server.configuration.adapter.FileNodeTypeAdapter;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.stool.server.logging.AccessLogEntry;
import net.oneandone.stool.server.logging.DetailsLogEntry;
import net.oneandone.stool.server.logging.LogReader;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.stool.server.users.UserManager;
import net.oneandone.stool.server.util.Predicate;
import net.oneandone.stool.server.util.SshDirectory;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.mail.MessagingException;
import java.io.FileNotFoundException;
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

    public static Server create(World world) throws IOException {
        String version;
        FileNode home;
        ServerConfiguration config;
        Server server;
        String localhostIp;
        version = Main.versionString(world);
        home = world.file("/var/lib/stool");
        boolean openShift;

        config = ServerConfiguration.load();
        LOGGER.info("server configuration: " + config);
        try (Engine engine = Engine.create()) {
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
            server = new Server(gson(world), version, openShift, home, localhostIp, config);
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

    /** CAUTION: not thread safe! */
    private final FileNode logRoot;

    public final String localhostIp;

    /** used read-only */
    public final ServerConfiguration configuration;

    public final UserManager userManager;

    public final Map<String, Accessor> accessors;

    public final SshDirectory sshDirectory;

    public Server(Gson gson, String version, boolean openShift, FileNode home, String localhostIp, ServerConfiguration configuration) throws IOException {
        this.gson = gson;
        this.version = version;
        this.world = home.getWorld();
        this.openShift = openShift;
        this.home = home;
        this.logRoot = home.join("logs");
        this.localhostIp = localhostIp;
        this.configuration = configuration;
        this.userManager = UserManager.loadOpt(home.join("users.json"));
        this.accessors = StageConfiguration.accessors();
        this.sshDirectory = SshDirectory.create(world.file("/home/stool/.ssh"));
    }

    public FileNode getLogs() {
        return logRoot;
    }

    /** @return last entry first; list may be empty because old log files are removed. */
    public List<AccessLogEntry> accessLog(String stage, int max, boolean modificationsOnly) throws IOException {
        AccessLogEntry entry;
        List<AccessLogEntry> entries;
        LogReader<AccessLogEntry> reader;
        String previousInvocation;

        entries = new ArrayList<>();
        reader = LogReader.accessLog(logRoot);
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
        reader = LogReader.detailsLog(logRoot);
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
        for (String name : StageConfiguration.list(engine)) {
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
        StageConfiguration c;

        try {
            c = StageConfiguration.load(gson, engine, name);
        } catch (FileNotFoundException e) {
            throw new StageNotFoundException(name, e);
        }
        return new Stage(this, name, c);
    }

    //--

    /** logs an error for administrators, i.e. the user is not expected to understand/fix this problem. */
    public void reportException(String command, String context, Throwable e) {
        String subject;
        StringWriter body;
        PrintWriter writer;

        LOGGER.error("[" + command + "] " + context + ": " + e.getMessage(), e);
        if (!configuration.admin.isEmpty()) {
            subject = "[stool exception] " + e.getMessage();
            body = new StringWriter();
            body.write("stool: " + Main.versionString(world) + "\n");
            body.write("command: " + command + "\n");
            body.write("context: " + context + "\n");
            body.write("user: " + MDC.get("USER") + "\n"); // TODO
            body.write("hostname: " + configuration.host + "\n");
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
                configuration.mailer().send(configuration.admin, new String[]{configuration.admin}, subject, body.toString());
            } catch (MessagingException suppressed) {
                LOGGER.error("cannot send exception email: " + suppressed.getMessage(), suppressed);
            }
        }
    }

    //--

    /** used for running containers */
    public int memoryReservedContainers(Engine engine, Registry registry) throws IOException {
        int reserved;
        PodInfo pod;

        reserved = 0;
        for (Stage stage : listAll(engine)) {
            pod = stage.runningPodOpt(engine);
            if (pod != null) {
                reserved += registry.info(pod).memory;
            }
        }
        return reserved;
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

    public int diskQuotaReserved(Engine engine, Registry registry) throws IOException {
        int reserved;
        Stage stage;
        Stage.Current current;

        reserved = 0;
        for (String name : StageConfiguration.list(engine)) {
            stage = load(engine, name);
            current = stage.currentOpt(engine, registry);
            if (current != null) {
                if (current.pod.containerId != null) {
                    reserved += current.image.disk;
                }
            }
        }
        return reserved;
    }

    //--

    public Registry createRegistry() throws IOException {
        String url;

        url = Strings.removeRight(configuration.registryUrl(), "/");
        return PortusRegistry.create(world, url, null);
    }

    /** @return path to generates certificate */
    public FileNode certificate(String certname) throws IOException {
        FileNode script;
        FileNode file;
        FileNode tmp;

        script = world.file("/var/lib/stool/cert.sh");
        if (!script.isFile()) {
            throw new IOException("don't know how to generate certificate: " + script.getAbsolute());
        }
        file = home.join("certs", certname);
        tmp = world.getTemp().createTempDirectory();  // fresh tmp directory to use the script for different stages concurrently
        LOGGER.debug(tmp.exec(script.getAbsolute(), certname, file.getAbsolute()));
        tmp.deleteTree();
        return file;
    }

    //--

    public void validate(Engine engine) throws IOException {
        configuration.validateRegistryUrl();
        if (configuration.auth()) {
            if (configuration.ldapSso.isEmpty()) {
                LOGGER.error("ldapSso cannot be empty because security is enabled");
                throw new IOException("ldapSso is empty");
            }
            if (System.getProperty("server.ssl.key-store") == null) {
                LOGGER.error("enable ssl when running authenticated");
                throw new IOException("enable ssl when running authenticated");
            }
        }
        try {
            LOGGER.info("docker info: " + engine.version());
        } catch (IOException e) {
            LOGGER.error("cannot access docker", e);
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
