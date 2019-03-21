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
package net.oneandone.stool.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.oneandone.inline.Console;
import net.oneandone.stool.cli.EnumerationFailed;
import net.oneandone.stool.cli.Main;
import net.oneandone.stool.configuration.Accessor;
import net.oneandone.stool.configuration.Expire;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.configuration.adapter.ExpireTypeAdapter;
import net.oneandone.stool.configuration.adapter.FileNodeTypeAdapter;
import net.oneandone.stool.docker.Engine;
import net.oneandone.stool.locking.LockManager;
import net.oneandone.stool.stage.Image;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.users.Users;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;

import javax.mail.MessagingException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Session {
    public static Session load(FileNode home, Logging logging, String command, Console console) throws IOException {
        Gson gson;

        gson = gson(home.getWorld());
        return new Session(gson, logging, command, home, console, StoolConfiguration.load(gson, home));
    }

    private static final int MEM_RESERVED_OS = 500;

    //--

    public final Gson gson;
    public final Logging logging;
    public final String user;
    private final String command;

    public final Console console;
    public final World world;
    public final FileNode home;
    public final StoolConfiguration configuration;

    private final FileNode stages;

    private final String stageIdPrefix;
    private int nextStageId;
    public final Users users;
    public final LockManager lockManager;

    private Map<String, Accessor> lazyAccessors;
    private Pool lazyPool;

    public Session(Gson gson, Logging logging, String command, FileNode home, Console console, StoolConfiguration configuration) {
        this.gson = gson;
        this.logging = logging;
        this.user = logging.getUser();
        this.command = command;
        this.console = console;
        this.world = home.getWorld();
        this.home = home;
        this.configuration = configuration;
        this.stages = home.join("stages");
        this.stageIdPrefix = logging.id + ".";
        this.nextStageId = 0;
        if (configuration.ldapUrl.isEmpty()) {
            this.users = Users.fromLogin();
        } else {
            this.users = Users.fromLdap(configuration.ldapUrl, configuration.ldapPrincipal, configuration.ldapCredentials,
                    "ou=users,ou=" + configuration.ldapUnit);
        }
        this.lockManager = LockManager.create(home.join("run/locks"), user + ":" + command.replace("\n", "\\n"), 30);
        this.lazyAccessors = null;
        this.lazyPool= null;
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

    public List<Stage> list(EnumerationFailed problems, Predicate predicate) throws IOException {
        List<Stage> result;
        Stage stage;

        result = new ArrayList<>();
        for (FileNode directory : stages.list()) {
            if (StageConfiguration.file(directory).exists()) {
                try {
                    stage = load(directory);
                } catch (IOException e) {
                    problems.add(directory.getName(), e);
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
        EnumerationFailed problems;

        problems = new EnumerationFailed();
        result = list(problems, new Predicate() {
            @Override
            public boolean matches(Stage stage) {
                return true;
            }
        });
        for (Map.Entry<String, Exception> entry : problems.problems.entrySet()) {
            reportException(entry.getKey() + ": Session.listAll", entry.getValue());
        }
        return result;
    }

    public List<String> stageNames() throws IOException {
        List<FileNode> directories;
        StageConfiguration config;
        List<String> result;

        directories = stages.list();
        result = new ArrayList<>(directories.size());
        for (FileNode directory : directories) {
            config = StageConfiguration.load(gson, StageConfiguration.file(directory));
            result.add(config.name);
        }
        return result;
    }

    //-- Stage create

    public Stage create(String origin) throws MkdirException {
        FileNode directory;
        StageConfiguration c;

        directory = stages.join(nextStageId()).mkdir();

        c = new StageConfiguration();
        c.url = configuration.vhosts ? "(http|https)://%a.%s.%h:%p/" : "(http|https)://%h:%p/";
        configuration.setDefaults(accessors(), c, origin);
        return new Stage(this, directory, c);
    }

    private String nextStageId() {
        nextStageId++;
        return stageIdPrefix + nextStageId;
    }


    //-- Stage access

    public Stage load(FileNode stage) throws IOException {
        return new Stage(this, stage, loadStageConfiguration(stage));
    }

    public Stage loadById(String id) throws IOException {
        return load(stages.join(id).checkDirectory());
    }

    public Stage loadByName(String stageName) throws IOException {
        List<FileNode> directories;
        StageConfiguration config;

        directories = stages.list();
        for (FileNode directory : directories) {
            config = StageConfiguration.load(gson, StageConfiguration.file(directory));
            if (stageName.equals(config.name)) {
                return load(directory);
            }
        }
        throw new IllegalArgumentException("stage not found: " + stageName);
    }

    private StageConfiguration loadStageConfiguration(FileNode stage) throws IOException {
        return StageConfiguration.load(gson, StageConfiguration.file(stage));
    }

    //-- selected stage

    private static final String UNKNOWN = "../unknown/..";
    private String lazySelectedId = UNKNOWN;

    public String getSelectedStageId() throws IOException {
        Project project;
        Stage stage;

        if (lazySelectedId == UNKNOWN) {
            project = Project.lookup(world.getWorking());
            if (project == null) {
                lazySelectedId = null;
            } else {
                stage = project.getAttachedOpt(this);
                if (stage != null) {
                    lazySelectedId = stage.reference.getId();
                }
            }
        }
        return lazySelectedId;
    }

    public boolean isSelected(Stage stage) throws IOException {
        return stage.reference.getId().equals(getSelectedStageId());
    }

    //--

    /** logs an error for administrators, i.e. the user is not expected to understand/fix this problem. */
    public void reportException(String context, Throwable e) {
        String subject;
        StringWriter body;
        PrintWriter writer;

        logging.error("[" + command + "] " + context + ": " + e.getMessage(), e);
        if (!configuration.admin.isEmpty()) {
            subject = "[stool exception] " + e.getMessage();
            body = new StringWriter();
            body.write("stool: " + Main.versionString(world) + "\n");
            body.write("command: " + command + "\n");
            body.write("context: " + context + "\n");
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
                logging.error("cannot send exception email: " + suppressed.getMessage(), suppressed);
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
        StageConfiguration stage;
        JsonObject json;
        Image image;
        FileNode directory;

        reserved = 0;
        engine = dockerEngine();
        for (String container : engine.containerListRunning(Stage.LABEL_STOOL).keySet()) {
            json = engine.containerInspect(container, false);
            image = Image.load(engine, Strings.removeLeft(json.get("Image").getAsString(), "sha256:"));
            directory = stages.join(image.id);
            if (directory.exists()) {
                stage = loadStageConfiguration(directory);
                reserved += stage.memory;
            } else {
                // TODO: stage container?
            }
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

    public FileNode downloads() {
        return home.join("downloads");
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

            log = home.join("logs/docker/" + user + ".log");
            log.deleteFileOpt();
            log.getParent().mkdirOpt();
            log.writeBytes();
            log.setPermissions("rw-------"); // only current user, because it might include tar files of the context directory - which is sensitive
            // TODO: does log-rotate preseve permissions?
            lazyEngine = Engine.open(configuration.docker, console.getVerbose() ? log.getAbsolute() : null);
        }
        return lazyEngine;
    }

    public void closeDockerEngine() {
        if (lazyEngine != null) {
            console.verbose.println("close docker engine");
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
        console.verbose.println(tmp.exec(script.getAbsolute(), certname, file.getAbsolute()));
        tmp.deleteTree();
        return file;
    }
}
