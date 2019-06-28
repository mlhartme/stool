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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.oneandone.stool.server.api.StageNotFoundException;
import net.oneandone.stool.server.configuration.Accessor;
import net.oneandone.stool.server.configuration.Expire;
import net.oneandone.stool.server.configuration.ServerConfiguration;
import net.oneandone.stool.server.configuration.StageConfiguration;
import net.oneandone.stool.server.configuration.adapter.ExpireTypeAdapter;
import net.oneandone.stool.server.configuration.adapter.FileNodeTypeAdapter;
import net.oneandone.stool.server.docker.ContainerInfo;
import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.logging.AccessLogEntry;
import net.oneandone.stool.server.logging.DetailsLogEntry;
import net.oneandone.stool.server.logging.LogReader;
import net.oneandone.stool.server.stage.Image;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.stool.server.users.UserManager;
import net.oneandone.stool.server.util.Pool;
import net.oneandone.stool.server.util.Predicate;
import net.oneandone.stool.server.util.SshDirectory;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
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

/** Immutable. */
public class Server {
    public static final Logger LOGGER = LoggerFactory.getLogger("DETAILS");

    public static Server create(World world) throws IOException {
        FileNode home;
        ServerConfiguration config;
        Pool pool;
        Server server;
        FileNode serverHome;
        FileNode secrets;
        JsonObject inspected;
        Map<String, String> binds;
        String networkMode;
        String localhostIp;

        home = world.file("/var/lib/stool");
        home(Main.versionString(world), home);

        config = ServerConfiguration.load();
        LOGGER.info("server configuration: " + config);
        try (Engine engine = Engine.create(config.engineLogFile())) {
            inspected = inspectSelf(engine);
            binds = binds(inspected);
            pool = config.loadPool(engine);
            serverHome = toHostFile(binds, world.file("/var/lib/stool"));
            secrets = toHostFile(binds, world.file("/etc/fault/workspace"));
            networkMode = networkMode(inspected);
            LOGGER.info("network mode: " + networkMode);
            localhostIp = InetAddress.getByName("localhost").getHostAddress();
            LOGGER.info("localhostIp: " + localhostIp);
            server = new Server(gson(world), home, serverHome, networkMode, localhostIp, secrets, config, pool);
            server.validate(engine);
            server.checkVersion();
            return server;
        }
    }

    private static FileNode toHostFile(Map<String, String> binds, FileNode container) throws IOException {
        String hostPath;

        hostPath = binds.get(container.getAbsolute());
        if (hostPath == null) {
            throw new IOException("no mapping found for " + container.getAbsolute() + ": " + binds);
        }
        return container.getWorld().file(hostPath);
    }

    private static JsonObject inspectSelf(Engine engine) throws IOException {
        String container;

        // container id is the default hostname for a Docker contaier
        container = InetAddress.getLocalHost().getCanonicalHostName();
        LOGGER.info("server container id: " + container);
        try {
            return engine.containerInspect(container, false);
        } catch (IOException e) {
            throw new IOException("cannot inspect server container' " + container + ": " + e.getMessage(), e);
        }
    }

    private static String networkMode(JsonObject inspected) throws IOException {
        JsonObject networks;

        networks = inspected.get("NetworkSettings").getAsJsonObject().get("Networks").getAsJsonObject();
        if (networks.size() != 1) {
            throw new IOException("unexpected Networks: " + networks);
        }
        return networks.keySet().iterator().next();
    }

    /** @return container- to host path mapping with absolute paths without tailing / */
    private static Map<String, String> binds(JsonObject inspected) throws IOException {
        List<String> modes = Strings.toList("ro", "rw");
        JsonArray binds;
        String str;
        int idx;
        Map<String, String> result;

        binds = inspected.get("HostConfig").getAsJsonObject().get("Binds").getAsJsonArray();
        result = new HashMap<>();
        for (JsonElement element : binds) {
            str = element.getAsString();
            LOGGER.info("bind: " + str);
            idx = str.lastIndexOf(':');
            if (idx == -1) {
                throw new IOException("unexpected bind: " + str);
            }
            if (!modes.contains(str.substring(idx + 1).toLowerCase())) {
                throw new IOException("unexpected mode in bind: " + str);
            }
            str = str.substring(0, idx);
            idx = str.indexOf(':');
            if (idx == -1) {
                throw new IOException("unexpected bind: " + str);
            }
            result.put(canonical(str.substring(idx + 1)), canonical(str.substring(0, idx)));
        }
        return result;
    }

    private static String canonical(String str) throws IOException {
        if (!str.startsWith("/")) {
            throw new IOException("absolute path expeccted: " + str);
        }
        if (str.endsWith("/")) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    //--

    public static void home(String version, FileNode home) throws IOException {
        FileNode homeVersionFile;
        String was;

        home.checkDirectory();
        homeVersionFile = home.join("version");
        if (homeVersionFile.isFile()) {
            was = homeVersionFile.readString().trim();
            if (was.equals(version)) {
                Server.LOGGER.info("using server home: " + home);
            } else {
                if (!Server.majorMinor(was).equals(Server.majorMinor(version))) {
                    throw new IOException("cannot update - migration needed: " + was + " -> " + version + ": " + home.getAbsolute());
                }
                Server.LOGGER.info("minor version change " + was + " -> " + version + ": " + home);
                homeVersionFile.writeString(version);
                Server.LOGGER.info("version number updated");
            }
        } else {
            Server.LOGGER.info("initializing server home " + home);
            initialize(home);
        }
    }

    private static void initialize(FileNode home) throws IOException {
        World world;
        FileNode dest;

        home.checkExists();
        world = home.getWorld();
        dest = home.join("cert.sh");
        if (dest.exists()) {
            // nothing to do -- cert.sh was probably set up by the server admins
        } else {
            world.resource("files/home/cert.sh").copyFile(dest);
            dest.setPermissions("rwx--x--x");
        }
        for (String name : new String[] {"stages", "certs"}) {
            home.join(name).mkdir();
        }
        home.join("templates").mkdirOpt();
        home.join("version").writeString(Main.versionString(world));
    }

    //--

    /** thread-save */
    public final Gson gson;
    private final FileNode home;
    private final FileNode logRoot;
    public final World world;
    public final String networkMode;
    public final String localhostIp;

    /** path so /var/lib/stool ON THE DOCKER HOST */
    public final FileNode serverHome;
    public final FileNode secrets;

    /** used read-only */
    public final ServerConfiguration configuration;

    public final Pool pool;

    private final FileNode stages;

    public final UserManager userManager;

    public final Map<String, Accessor> accessors;

    public final SshDirectory sshDirectory;

    public Server(Gson gson, FileNode home, FileNode serverHome, String networkMode, String localhostIp,
                  FileNode secrets, ServerConfiguration configuration, Pool pool) throws IOException {
        this.gson = gson;
        this.home = home;
        this.logRoot = home.join("logs");
        this.world = home.getWorld();
        this.serverHome = serverHome;
        this.networkMode = networkMode;
        this.localhostIp = localhostIp;
        this.secrets = secrets;
        this.configuration = configuration;
        this.pool = pool;
        this.stages = home.join("stages");
        this.userManager = UserManager.loadOpt(home.join("users.json"));
        this.accessors = StageConfiguration.accessors();
        this.sshDirectory = SshDirectory.create(world.file("/home/stool/.ssh"));
    }

    public LogReader<AccessLogEntry> accessLogReader() throws IOException {
        return LogReader.accessLog(logRoot);
    }

    public LogReader<DetailsLogEntry> detailsLogReader() throws IOException {
        return LogReader.detailsLog(logRoot);
    }

    public List<DetailsLogEntry> detailsLog(String clientInvocation) throws IOException {
        DetailsLogEntry entry;
        List<DetailsLogEntry> entries;
        LogReader<DetailsLogEntry> reader;

        entries = new ArrayList<>();
        reader = detailsLogReader();
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

    public FileNode templates() {
        return home.join("templates");
    }

    //-- Stage listings

    public List<Stage> list(Predicate predicate, Map<String, IOException> problems) throws IOException {
        List<Stage> result;
        Stage stage;

        result = new ArrayList<>();
        for (FileNode directory : stages.list()) {
            if (StageConfiguration.file(directory).exists()) {
                try {
                    stage = load(directory);
                } catch (IOException e) {
                    problems.put(directory.getAbsolute(), e);
                    continue;
                }
                if (predicate.matches(stage)) {
                    result.add(stage);
                }
            } else {
                // stage is being created, we're usually waiting the the checkout to complete
            }
        }
        return result;
    }

    public List<Stage> listAll() throws IOException {
        List<Stage> result;
        Map<String, IOException> problems;

        problems = new HashMap<>();
        result = list(new Predicate() {
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

    //-- Stage create

    public Stage create(String name) throws MkdirException {
        return new Stage(this, stages.join(name).mkdir(), new StageConfiguration());
    }

    //-- Stage access

    public Stage load(FileNode stage) throws IOException {
        return new Stage(this, stage, loadStageConfiguration(stage));
    }

    public Stage load(String name) throws IOException {
        FileNode directory;

        directory = stages.join(name);
        if (directory.exists()) {
            return load(directory);
        } else {
            throw new StageNotFoundException(name);
        }
    }

    private StageConfiguration loadStageConfiguration(FileNode stage) throws IOException {
        return StageConfiguration.load(gson, StageConfiguration.file(stage));
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
            body.write("hostname: " + configuration.dockerHost + "\n");
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
    public int memoryReservedContainers(Engine engine) throws IOException {
        int reserved;
        Image image;

        reserved = 0;
        for (ContainerInfo info : engine.containerListRunning(Stage.CONTAINER_LABEL_IMAGE).values()) {
            image = Image.load(engine, info.labels.get(Stage.CONTAINER_LABEL_IMAGE));
            reserved += image.memory;
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

    public void checkVersion() throws IOException {
        String homeVersion;
        String binVersion;

        homeVersion = home.join("version").readString().trim();
        binVersion = Main.versionString(world);
        if (!homeVersion.equals(binVersion)) {
            throw new IOException("Cannot use home directory version " + homeVersion + " with Stool " + binVersion
               + "\nTry 'stool setup'");
        }
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

    public int diskQuotaReserved(Engine engine) throws IOException {
        int reserved;
        Stage stage;
        ContainerInfo info;

        reserved = 0;
        for (FileNode directory : stages.list()) {
            stage = load(directory);
            for (Stage.Current current : stage.currentMap(engine).values()) {
                info = current.container;
                if (info != null) {
                    reserved += current.image.disk;
                }
            }
        }
        return reserved;
    }

    //--

    public FileNode certificate(String certname) throws IOException {
        FileNode script;
        FileNode file;
        FileNode tmp;

        script = world.file("/var/lib/stool/cert.sh");
        if (script == null || !script.isFile()) {
            throw new IOException("don't know how to generate certificate: " + script);
        }
        file = home.join("certs", certname);
        tmp = world.getTemp().createTempDirectory();
        LOGGER.debug(tmp.exec(script.getAbsolute(), certname, file.getAbsolute()));
        tmp.deleteTree();
        return serverHome.join(file.getRelative(home));
    }

    //--

    public void validate(Engine engine) throws IOException {
        Engine.validateReference(configuration.registryNamespace);
        if (configuration.auth()) {
            if (configuration.ldapSso.isEmpty()) {
                LOGGER.error("ldapsso cannot be empty because security is enabled");
                throw new IOException("ldapsso is empty");
            }
            if (System.getProperty("server.ssl.key-store") == null) {
                LOGGER.error("enable ssl when running authenticated");
                throw new IOException("enable ssl when running authenticated");
            }
        }
        try {
            engine.imageList();
        } catch (IOException e) {
            LOGGER.error("cannot access docker", e);
            throw e;
        }
        LOGGER.info("server validation ok");
    }
}
