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
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import net.oneandone.stool.kubernetes.Registry;
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
import net.oneandone.stool.server.stage.TagInfo;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.stool.server.users.UserManager;
import net.oneandone.stool.server.util.Pool;
import net.oneandone.stool.server.util.Predicate;
import net.oneandone.stool.server.util.SshDirectory;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.HttpNode;
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

    public static Server create(World world) throws IOException {
        String version;
        FileNode home;
        ServerConfiguration config;
        Pool pool;
        Server server;
        FileNode serverHome;
        FileNode secrets;
        Map<String, String> binds;
        String localhostIp;

        version = Main.versionString(world);
        home = world.file("/var/lib/stool");
        home(version, home);

        config = ServerConfiguration.load();
        LOGGER.info("server configuration: " + config);
        try (Engine engine = Engine.create()) {
            binds = binds(engine);
            pool = config.loadPool(engine);
            serverHome = toHostFile(binds, world.file("/var/lib/stool"));
            secrets = toHostFile(binds, world.file("/etc/fault/workspace"));
            localhostIp = InetAddress.getByName("localhost").getHostAddress();
            LOGGER.info("localhostIp: " + localhostIp);
            server = new Server(gson(world), version, home, serverHome, localhostIp, secrets, config, pool);
            server.validate(engine);
            server.checkVersion();
            return server;
        }
    }

    private static Map<String, String> binds(Engine engine) throws IOException {
        V1Pod pod;
        Map<String, String> result;
        List<V1Container> lst;
        V1Container container;

        pod = engine.podRaw("stool-server");
        if (pod == null) {
            throw new IOException("stool-server not found");
        }
        lst = pod.getSpec().getContainers();
        if (lst == null || lst.size() != 1) {
            throw new IOException("1 container expected: " + lst);
        }
        container = lst.get(0);
        result = new HashMap<>();
        for (V1Volume volume : pod.getSpec().getVolumes()) {
            if (volume.getHostPath() != null) {
                result.put(mount(container, volume.getName()), volume.getHostPath().getPath());
            } else {
                System.out.println("ignore volume: " + volume);
            }
        }
        return result;
    }

    private static String mount(V1Container container, String name) throws IOException {
        for (V1VolumeMount mount : container.getVolumeMounts()) {
            if (name.equals(mount.getName())) {
                return mount.getMountPath();
            }
        }
        throw new IOException("volume mount not found: " + name);
    }

    private static FileNode toHostFile(Map<String, String> binds, FileNode container) throws IOException {
        String hostPath;

        hostPath = binds.get(container.getAbsolute());
        if (hostPath == null) {
            throw new IOException("no mapping found for " + container.getAbsolute() + ": " + binds);
        }
        return container.getWorld().file(hostPath);
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
            initializeHome(version, home);
        }
    }

    private static void initializeHome(String version, FileNode home) throws IOException {
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
        home.join("version").writeString(version);
    }

    //--

    /** gson is thread-save */
    public final Gson gson;

    public final String version;

    /** CAUTION: not thread safe! Try to use engine.world instead */
    private final World world;

    /** CAUTION: not thread safe! */
    private final FileNode home;

    /** CAUTION: not thread safe! */
    private final FileNode logRoot;

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

    public Server(Gson gson, String version, FileNode home, FileNode serverHome, String localhostIp,
                  FileNode secrets, ServerConfiguration configuration, Pool pool) throws IOException {
        this.gson = gson;
        this.version = version;
        this.world = home.getWorld();
        this.home = home;
        this.logRoot = home.join("logs");
        this.serverHome = serverHome;
        this.localhostIp = localhostIp;
        this.secrets = secrets;
        this.configuration = configuration;
        this.pool = pool;
        this.stages = home.join("stages");
        this.userManager = UserManager.loadOpt(home.join("users.json"));
        this.accessors = StageConfiguration.accessors();
        this.sshDirectory = SshDirectory.create(world.file("/home/stool/.ssh"));
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


    public Stage create(String name) throws StageExistsException {
        FileNode dir;

        try {
            dir = stages.join(name).mkdir();
        } catch (MkdirException e) {
            throw new StageExistsException();
        }
        return new Stage(this, dir, new StageConfiguration());
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
    public int memoryReservedContainers(Engine engine, Registry registry) throws IOException {
        int reserved;
        TagInfo image;

        reserved = 0;
        for (PodInfo pod : Stage.allPodMap(engine).values()) { // TODO: expensive
            if (pod.isRunning()) {
                image = registry.info(pod);
                reserved += image.memory;
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

    public int diskQuotaReserved(Engine engine, Registry registry) throws IOException {
        int reserved;
        Stage stage;
        Stage.Current current;

        reserved = 0;
        for (FileNode directory : stages.list()) {
            stage = load(directory);
            current = stage.currentOpt(engine, registry);
            if (current != null) {
                if (current.pod.containerId != null) {
                    reserved += current.image.disk;
                }
            }
        }
        return reserved;
    }

    public Registry createRegistry(Engine engine) throws IOException {
        PodInfo info;
        HttpNode root;

        info = engine.podProbe("stool-registry");
        if (info == null) {
            throw new IOException("registry not found");
        }
        root = (HttpNode) World.create().validNode("http://" + info.ip + ":5000");
        return Registry.local(root, null);
    }

    //--

    /** @return path to generates certificate */
    public FileNode certificate(String certname) throws IOException {
        FileNode script;
        FileNode file;
        FileNode tmp;

        script = world.file("/var/lib/stool/cert.sh");
        if (script == null || !script.isFile()) {
            throw new IOException("don't know how to generate certificate: " + script);
        }
        file = home.join("certs", certname);
        tmp = world.getTemp().createTempDirectory();  // fresh tmp directory to use the script for different stages concurrently
        LOGGER.debug(tmp.exec(script.getAbsolute(), certname, file.getAbsolute()));
        tmp.deleteTree();
        return file;
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
