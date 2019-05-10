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
import net.oneandone.stool.server.configuration.Accessor;
import net.oneandone.stool.server.configuration.Expire;
import net.oneandone.stool.server.configuration.ServerConfiguration;
import net.oneandone.stool.server.configuration.StageConfiguration;
import net.oneandone.stool.server.configuration.adapter.ExpireTypeAdapter;
import net.oneandone.stool.server.configuration.adapter.FileNodeTypeAdapter;
import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.logging.AccessLogEntry;
import net.oneandone.stool.server.logging.DetailsLogEntry;
import net.oneandone.stool.server.logging.LogReader;
import net.oneandone.stool.server.stage.Image;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.stool.server.users.UserManager;
import net.oneandone.stool.server.util.Pool;
import net.oneandone.stool.server.util.Predicate;
import net.oneandone.stool.server.api.StageNotFoundException;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Diff;
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
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable */
public class Server {
    public static final Logger LOGGER = LoggerFactory.getLogger("DETAILS");

    public static Server create(World world) throws IOException {
        FileNode home;
        ServerConfiguration config;
        Server server;
        Engine engine;
        FileNode serverHome;
        FileNode secrets;
        Map<String, String> binds;

        home = world.file("/var/lib/stool");
        home(Main.versionString(world), home);

        config = ServerConfiguration.load();
        LOGGER.info("server configuration: " + config);

        engine = Engine.open("/var/run/docker.sock", null /* TODO dockerLog(logRoot).getAbsolute() */);

        binds = binds(engine);
        serverHome = toHostFile(binds, world.file("/var/lib/stool"));
        secrets = toHostFile(binds, world.file("/etc/fault/workspace"));

        server = new Server(gson(world), home, engine, serverHome, secrets, config);
        server.validate();
        server.checkVersion();
        return server;
    }

    private static FileNode toHostFile(Map<String, String> binds, FileNode container) throws IOException {
        String hostPath;

        hostPath = binds.get(container.getAbsolute());
        if (hostPath == null) {
            throw new IOException("no mapping found for " + container.getAbsolute() + ": " + binds);
        }
        return container.getWorld().file(hostPath);
    }

    /** @return container- to host path mapping with absolute paths without tailing / */
    private static Map<String, String> binds(Engine engine) throws IOException {
        List<String> modes = Strings.toList("ro", "rw");
        String container;
        JsonObject inspected;
        JsonArray binds;
        String str;
        int idx;
        Map<String, String> result;

        // container id is the default hostname for a Docker contaier
        container = InetAddress.getLocalHost().getCanonicalHostName();
        LOGGER.info("server container id: " + container);
        try {
            inspected = engine.containerInspect(container, false);
        } catch (IOException e) {
            throw new IOException("cannot inspect server container' " + container + ": " + e.getMessage(), e);
        }
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
                Server.LOGGER.info("Updating server home " + was + " -> " + version + ": " + home);
                update(version, home);
            }
        } else {
            Server.LOGGER.info("initializing server home " + home);
            initialize(home);
        }
    }

    private static void initialize(FileNode home) throws IOException {
        World world;

        home.checkExists();
        world = home.getWorld();
        world.resource("files/home").copyDirectory(home);
        home.join("cert.sh").setPermissions("rwx--x--x"); // ugly special case ...
        for (String name : new String[]{"stages","certs"}) {
            home.join(name).mkdir();
        }
        home.join("templates").mkdirOpt();
        home.join("version").writeString(Main.versionString(world));
    }

    private static final List<String> CONFIG = Strings.toList();

    private static void update(String version, FileNode home) throws IOException {
        String was;
        FileNode fresh;
        FileNode dest;
        String path;
        String left;
        String right;
        int count;

        was = home.join("version").readString().trim();
        if (!Server.majorMinor(was).equals(Server.majorMinor(version))) {
            throw new IOException("cannot update - migration needed: " + was + " -> " + version + ": " + home.getAbsolute());
        }
        fresh = home.getWorld().getTemp().createTempDirectory();
        fresh.deleteDirectory();
        initialize(fresh);
        count = 0;
        for (FileNode src : fresh.find("**/*")) {
            if (!src.isFile()) {
                continue;
            }
            path = src.getRelative(fresh);
            if (CONFIG.contains(path)) {
                continue;
            }
            dest = home.join(path);
            left = src.readString();
            right = dest.readString();
            if (!left.equals(right)) {
                Server.LOGGER.info("U " + path);
                Server.LOGGER.info(Strings.indent(Diff.diff(right, left), "  "));
                dest.writeString(left);
                count++;
            }
        }
        fresh.deleteTree();
        Server.LOGGER.info("Done, " + count  + " file(s) updated.");
    }

    //--

    public final Gson gson;
    public final FileNode home;
    private final FileNode logRoot;
    public final World world;
    private final Engine dockerEngine;

    /** path so /var/lib/stool ON THE DOCKER HOST */
    public final FileNode serverHome;
    public final FileNode secrets;

    public final ServerConfiguration configuration;

    private final FileNode stages;

    public final UserManager userManager;

    public Map<String, Accessor> accessors;

    public Server(Gson gson, FileNode home, Engine engine, FileNode serverHome, FileNode secrets, ServerConfiguration configuration) throws IOException {
        this.gson = gson;
        this.home = home;
        this.logRoot = home.join("logs");
        this.world = home.getWorld();
        this.dockerEngine = engine;
        this.serverHome = serverHome;
        this.secrets = secrets;
        this.configuration = configuration;
        this.stages = home.join("stages");
        this.userManager = UserManager.loadOpt(home.join("users.json"));
        this.accessors = StageConfiguration.accessors();
    }

    private static FileNode dockerLog(FileNode logRoot) throws IOException {
        FileNode log;

        log = logRoot.join("docker.log");
        log.deleteFileOpt();
        log.getParent().mkdirOpt();
        log.writeBytes();
        return log;
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

    public List<String> stageNames() throws IOException {
        List<FileNode> directories;
         List<String> result;

        directories = stages.list();
        result = new ArrayList<>(directories.size());
        for (FileNode directory : directories) {
            result.add(directory.getName());
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
    public int memoryReservedContainers() throws IOException {
        int reserved;
        Engine engine;
        JsonObject json;
        Image image;

        reserved = 0;
        engine = dockerEngine();
        for (String container : engine.containerListRunning(Stage.CONTAINER_LABEL_IMAGE).keySet()) {
            json = engine.containerInspect(container, false);
            image = Image.load(engine, containerImageTag(json));
            reserved += image.memory;
        }
        return reserved;
    }

    public static String containerImageTag(JsonObject inspected) {
        return inspected.get("Config").getAsJsonObject().get("Labels").getAsJsonObject().get(Stage.CONTAINER_LABEL_IMAGE).getAsString();
    }

    //-- stool properties

    public Pool pool() throws IOException {
        return Pool.load(dockerEngine(), configuration.portFirst + 4 /* 4 ports reserved for the server (http(s), debug, jmx, unused) */,
                configuration.portLast);
    }

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

    public int diskQuotaReserved() throws IOException {
        int reserved;
        Stage stage;
        Engine.ContainerInfo info;

        reserved = 0;
        for (FileNode directory : stages.list()) {
            stage = load(directory);
            for (Map.Entry<String, Stage.Current> entry : stage.currentMap().entrySet()) {
                info = entry.getValue().container;
                if (info != null) {
                    reserved += entry.getValue().image.disk;
                }
            }
        }
        return reserved;
    }

    //--

    public Engine dockerEngine() {
        return dockerEngine;
    }

    public void closeDockerEngine() { // TODO: invoke on server shut-down
        LOGGER.debug("close docker engine");
        dockerEngine.close();
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


    public void validate() throws IOException {
        try {
            InetAddress.getByName(configuration.dockerHost);
        } catch (UnknownHostException e) {
            LOGGER.error("cannot resolve docker host name: " + e.getMessage(), e);
            throw e;
        }
        try {
            dockerEngine().imageList();
        } catch (IOException e) {
            LOGGER.error("cannot access docker", e);
            throw e;
        }
        LOGGER.info("server validation ok");
    }
}
