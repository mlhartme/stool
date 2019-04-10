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
package net.oneandone.stool.server.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.oneandone.stool.server.cli.Main;
import net.oneandone.stool.server.configuration.Accessor;
import net.oneandone.stool.server.configuration.Expire;
import net.oneandone.stool.server.configuration.StageConfiguration;
import net.oneandone.stool.server.configuration.StoolConfiguration;
import net.oneandone.stool.server.configuration.adapter.ExpireTypeAdapter;
import net.oneandone.stool.server.configuration.adapter.FileNodeTypeAdapter;
import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.logging.AccessLogEntry;
import net.oneandone.stool.server.logging.LogReader;
import net.oneandone.stool.server.stage.Image;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.stool.server.users.Users;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.MessagingException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Server {
    public static final Logger LOGGER = LoggerFactory.getLogger("DETAILS");


    public static Server load(FileNode home, FileNode logRoot) throws IOException {
        Gson gson;

        gson = gson(home.getWorld());
        return new Server(gson, logRoot, home, StoolConfiguration.load(gson, home));
    }

    private static final int MEM_RESERVED_OS = 500;

    //--

    public final Gson gson;
    public final FileNode logRoot;

    // TODO: per-request data
    public final String user;

    public final World world;
    public final FileNode home;
    public final StoolConfiguration configuration;

    private final FileNode stages;

    public final Users users;

    private Map<String, Accessor> lazyAccessors;
    private Pool lazyPool;

    public Server(Gson gson, FileNode logRoot, FileNode home, StoolConfiguration configuration) {
        this.gson = gson;
        this.logRoot = logRoot;
        this.user = Environment.detectUser();
        this.world = home.getWorld();
        this.home = home;
        this.configuration = configuration;
        this.stages = home.join("stages");
        if (configuration.ldapUrl.isEmpty()) {
            this.users = Users.fromLogin();
        } else {
            this.users = Users.fromLdap(configuration.ldapUrl, configuration.ldapPrincipal, configuration.ldapCredentials,
                    "ou=users,ou=" + configuration.ldapUnit);
        }
        this.lazyAccessors = null;
        this.lazyPool= null;
    }

    public LogReader<AccessLogEntry> accessLogReader() throws IOException {
        return LogReader.accessLog(logRoot);
    }

    public Map<String, Accessor> accessors() {
        if (lazyAccessors == null) {
            lazyAccessors = StageConfiguration.accessors();
        }
        return lazyAccessors;
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
        return load(stages.join(name).checkDirectory());
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
            body.write("server: " + context + "\n");
            body.write("user: " + user + "\n");
            body.write("hostname: " + configuration.hostname + "\n");
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

    //-- environment handling

    private static int memTotal() {
        long result;

        result = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalPhysicalMemorySize();
        return (int) (result / 1024 / 1024);
    }

    //--

    /** @return memory not yet reserved */
    public int memUnreserved() throws IOException {
        return memTotal() - MEM_RESERVED_OS - memReservedContainers();
    }

    /** used for running containers */
    private int memReservedContainers() throws IOException {
        int reserved;
        Engine engine;
        JsonObject json;
        Image image;

        reserved = 0;
        engine = dockerEngine();
        for (String container : engine.containerListRunning(Stage.CONTAINER_LABEL_STOOL).keySet()) {
            json = engine.containerInspect(container, false);
            image = Image.load(engine, Strings.removeLeft(json.get("Image").getAsString(), "sha256:"));
            reserved += image.memory;
        }
        return reserved;
    }

    //-- stool properties

    public Pool pool() throws IOException {
        if (lazyPool == null) {
            lazyPool = Pool.load(dockerEngine(), configuration.portFirst, configuration.portLast);
        }
        return lazyPool;
    }

    public void updatePool() { // TODO: hack to see updated application urls
        lazyPool = null;
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

    public int quotaReserved() throws IOException {
        int reserved;
        StageConfiguration config;

        reserved = 0;
        for (FileNode directory : stages.list()) {
            config = loadStageConfiguration(directory);
            reserved += Math.max(0, config.quota);
        }
        return reserved;
    }

    //--

    private Engine lazyEngine = null;

    public Engine dockerEngine() throws IOException {
        if (lazyEngine == null) {
            FileNode log;

            log = logRoot.join("docker/" + user + ".log");
            log.deleteFileOpt();
            log.getParent().mkdirOpt();
            log.writeBytes();
            lazyEngine = Engine.open(configuration.docker, log.getAbsolute());
        }
        return lazyEngine;
    }

    public void closeDockerEngine() { // TODO: invoke on server shut-down
        if (lazyEngine != null) {
            LOGGER.debug("close docker engine");
            lazyEngine.close();
        }
    }

    //--

    public FileNode certificate(String certname) throws IOException {
        FileNode script;
        FileNode file;
        FileNode tmp;

        script = configuration.certificate;
        if (script == null || !script.isFile()) {
            throw new IOException("don't know how to generate certifcate: " + script);
        }
        file = home.join("certs", certname);
        tmp = world.getTemp().createTempDirectory();
        LOGGER.debug(tmp.exec(script.getAbsolute(), certname, file.getAbsolute()));
        tmp.deleteTree();
        return file;
    }
}
