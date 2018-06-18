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
import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.maven.embedded.Maven;
import net.oneandone.setenv.Setenv;
import net.oneandone.stool.cli.EnumerationFailed;
import net.oneandone.stool.cli.Main;
import net.oneandone.stool.configuration.Accessor;
import net.oneandone.stool.configuration.Bedroom;
import net.oneandone.stool.configuration.Expire;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.configuration.adapter.ExpireTypeAdapter;
import net.oneandone.stool.configuration.adapter.FileNodeTypeAdapter;
import net.oneandone.stool.docker.Engine;
import net.oneandone.stool.locking.LockManager;
import net.oneandone.stool.scm.Scm;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.users.Users;
import net.oneandone.sushi.fs.LinkException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import org.codehaus.plexus.DefaultPlexusContainer;

import javax.mail.MessagingException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Session {
    public static Session load(boolean setenv, FileNode home, Logging logging, String command, Console console, World world,
                               String svnuser, String svnpassword) throws IOException {
        Session session;

        session = loadWithoutBackstageWipe(setenv, home, logging, command, console, world, svnuser, svnpassword);

        // Stale backstage wiping: how to detect backstage directories who's stage directory was removed.
        //
        // My first thought was to watch for filesystem events to trigger backstage wiping.
        // But there's quite a big delay and rmdir+mkdir is reported as modification.
        // Plus the code is quite complex and I don't know how to handle overflow events.
        //
        // So I simply wipe them whenever I load stool a session. That's a well-defined timing and that's before
        // Stool might use a stale stage.
        session.wipeStaleBackstages();
        return session;
    }

    public void wipeStaleBackstages() throws IOException {
        long s;
        Path path;

        s = System.currentTimeMillis();
        for (FileNode link : backstages.list()) {
            path = link.toPath();
            if (!java.nio.file.Files.isSymbolicLink(path)) {
                console.error.println("error: symbolic link expected: " + path);
            } else {
                path = java.nio.file.Files.readSymbolicLink(path);
                if (!java.nio.file.Files.exists(path)) {
                    if (accessDenied(path)) {
                        console.error.println("stage is not accessible: " + path);
                    } else {
                        console.info.println("removing stale backstage link: " + link);
                        link.deleteTree();
                    }
                }
            }
        }
        console.verbose.println("wipeStaleBackstages done, ms=" + ((System.currentTimeMillis() - s)));
    }

    private static boolean accessDenied(Path dir) {
        while (dir != null) {
            if (java.nio.file.Files.isDirectory(dir)) {
                if (!java.nio.file.Files.isReadable(dir)) {
                    return true;
                }
                if (!java.nio.file.Files.isExecutable(dir)) {
                    return true;
                }
            }
            dir = dir.getParent();
        }
        return false;
    }

    private static Session loadWithoutBackstageWipe(boolean setenv, FileNode home, Logging logging, String command, Console console,
                                                  World world, String svnuser, String svnpassword) throws IOException {
        Gson gson;
        Session result;

        gson = gson(world);
        result = new Session(setenv, gson, logging, command, home, console, world,
                StoolConfiguration.load(gson, home), Bedroom.loadOrCreate(gson, home), svnuser, svnpassword);
        return result;
    }

    private static final int MEM_RESERVED_OS = 500;

    //--

    private final boolean setenv;
    public final Gson gson;
    public final Logging logging;
    public final String user;
    private final String command;

    public final FileNode home;

    public final Console console;
    public final World world;
    public final StoolConfiguration configuration;
    public final Bedroom bedroom;

    private final FileNode backstages;

    private final Credentials svnCredentials;

    private final String stageIdPrefix;
    private int nextStageId;
    public final Users users;
    public final LockManager lockManager;

    private Map<String, Accessor> lazyAccessors;
    private Pool lazyPool;

    public Session(boolean setenv, Gson gson, Logging logging, String command,
                   FileNode home, Console console, World world, StoolConfiguration configuration,
                   Bedroom bedroom, String svnuser, String svnpassword) {
        this.setenv = setenv;
        this.gson = gson;
        this.logging = logging;
        this.user = logging.getUser();
        this.command = command;
        this.home = home;
        this.console = console;
        this.world = world;
        this.configuration = configuration;
        this.bedroom = bedroom;
        this.backstages = home.join("backstages");
        this.svnCredentials = new Credentials(svnuser, svnpassword);
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
            lazyAccessors = StageConfiguration.accessors(templates());
        }
        return lazyAccessors;
    }

    public FileNode templates() {
        return home.join("templates");
    }

    public void add(FileNode backstage, String id) throws LinkException {
        backstage.link(backstages.join(id));
    }

    public FileNode backstageLink(String id) {
        return backstages.join(id);
    }

    public FileNode findStageDirectory(FileNode dir) {
        do {
            if (Stage.backstageDirectory(dir).exists()) {
                return dir;
            }
            dir = dir.getParent();
        } while (dir != null);
        return null;
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

    public List<Stage> list(EnumerationFailed problems, Predicate predicate) throws IOException {
        List<Stage> result;
        Stage stage;
        FileNode backstage;

        result = new ArrayList<>();
        for (FileNode link : backstages.list()) {
            backstage = link.resolveLink();
            if (StageConfiguration.file(backstage).exists()) {
                try {
                    stage = Stage.load(this, link);
                } catch (IOException e) {
                    problems.add(link.getName(), e);
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

    public List<Stage> listWithoutSystem() throws IOException {
        List<Stage> result;
        EnumerationFailed problems;

        problems = new EnumerationFailed();
        result = list(problems, new Predicate() {
            @Override
            public boolean matches(Stage stage) {
                return !stage.isSystem();
            }
        });
        for (Map.Entry<String, Exception> entry : problems.problems.entrySet()) {
            reportException(entry.getKey() + ": Session.listWithoutDashboard", entry.getValue());
        }
        return result;
    }

    public Scm scm(String url) throws IOException {
        return Scm.forUrl(url, svnCredentials);
    }

    private Scm scmOpt(String url) {
        return Scm.forUrlOpt(url, svnCredentials);
    }

    public Credentials svnCredentials() {
        return svnCredentials;
    }

    public Stage load(String id) throws IOException {
        return Stage.load(this, backstages.join(id));
    }

    public Stage loadByName(String stageName) throws IOException {
        List<FileNode> links;
        FileNode bs;
        StageConfiguration config;

        links = backstages.list();
        for (FileNode link : links) {
            bs = link.resolveLink();
            config = StageConfiguration.load(gson, StageConfiguration.file(bs));
            if (stageName.equals(config.name)) {
                return load(link.getName());
            }
        }
        throw new IllegalArgumentException("stage not found: " + stageName);
    }

    public List<String> stageNames() throws IOException {
        List<FileNode> links;
        FileNode bs;
        StageConfiguration config;
        List<String> result;

        links = backstages.list();
        result = new ArrayList<>(links.size());
        for (FileNode link : links) {
            bs = link.resolveLink();
            config = StageConfiguration.load(gson, StageConfiguration.file(bs));
            result.add(config.name);
        }
        return result;
    }

    /** return directory or null */
    public FileNode lookup(String stageName) throws IOException {
        List<FileNode> links;
        FileNode bs;
        StageConfiguration config;

        links = backstages.list();
        for (FileNode link : links) {
            bs = link.resolveLink();
            config = StageConfiguration.load(gson, StageConfiguration.file(bs));
            if (stageName.equals(config.name)) {
                return bs.getParent();
            }
        }
        return null;
    }

    private static final String UNKNOWN = "../unknown/..";
    private String lazySelectedId = UNKNOWN;

    public String getSelectedStageId() throws IOException {
        FileNode directory;
        FileNode bs;

        if (lazySelectedId == UNKNOWN) {
            directory = findStageDirectory(world.getWorking());
            if (directory == null) {
                lazySelectedId = null;
            } else {
                bs = Stage.backstageDirectory(directory);
                for (FileNode link : backstages.list()) {
                    if (link.resolveLink().equals(bs)) {
                        lazySelectedId = link.getName();
                        break;
                    }
                }
                if (lazySelectedId == UNKNOWN) {
                    // directory has a backstage directory, but there's no link to it in stool/backstages
                    // -> directory is not a stage
                    lazySelectedId = null;
                }
            }
        }
        return lazySelectedId;
    }

    /** returns the build environment */
    public Environment environment(Stage stage) {
        Environment env;
        String mavenOpts;

        if (stage == null) {
            mavenOpts = "";
        } else {
            mavenOpts = stage.macros().replace(stage.config().mavenOpts);
        }
        env = new Environment();
        env.setMavenHome((stage != null && stage.config().mavenHome() != null) ? stage.config().mavenHome() : null);
        env.setMavenOpts(mavenOpts);
        return env;
    }

    //--

    /** @return memory not yet reserved */
    public int memUnreserved() throws IOException {
        return memTotal() - MEM_RESERVED_OS - memReservedContainers();
    }

    /** used for running containers */
    private int memReservedContainers() throws IOException {
        int reserved;
        StageConfiguration stage;
        FileNode backstage;

        reserved = 0;
        for (FileNode link : backstages.list()) {
            backstage = link.resolveLink();
            if (backstage.join("container.id").exists()) {
                stage = loadStageConfiguration(backstage);
                reserved += stage.memory;
            }
        }
        return reserved;
    }

    public void checkDiskFree(FileNode directory) {
        int free;
        int min;

        free = diskFree(directory);
        min = configuration.diskMin;
        if (free < min) {
            throw new ArgumentException("Disk almost full. Currently available " + free + " mb, required " + min + " mb.");
        }
    }

    /** @return Free disk space in partition used for stool lib. TODO: not necessarily the partition used for stages. */
    private int diskFree(FileNode directory) {
        return (int) (directory.toPath().toFile().getUsableSpace() / 1024 / 1024);
    }

    public FileNode ports() {
        return home.join("run/ports");
    }

    public boolean isSelected(Stage stage) throws IOException {
        return stage.getId().equals(getSelectedStageId());
    }

    //-- stage properties


    public void saveStageProperties(StageConfiguration stageConfiguration, FileNode backstage) throws IOException {
        stageConfiguration.save(gson, StageConfiguration.file(backstage));
    }

    public StageConfiguration loadStageConfiguration(FileNode backstage) throws IOException {
        return StageConfiguration.load(gson, StageConfiguration.file(backstage));
    }

    //-- stool properties

    public List<FileNode> stageDirectories() throws IOException {
        List<FileNode> result;

        result = new ArrayList<>();
        for (FileNode link : backstages.list()) {
            result.add(link.resolveLink().getParent());
        }
        return result;
    }

    public Pool pool() throws IOException {
        if (lazyPool == null) {
            lazyPool = Pool.loadOpt(ports(), configuration.portFirst, configuration.portLast, backstages);
        }
        return lazyPool;
    }

    public void updatePool() { // TODO: hack to see updates application urls
        lazyPool = null;
    }

    public StageConfiguration createStageConfiguration(String url) {
        String mavenHome;
        StageConfiguration result;
        Scm scm;
        String refresh;

        try {
            mavenHome = Maven.locateMaven(world).getAbsolute();
        } catch (IOException e) {
            mavenHome = "";
        }
        scm = scmOpt(url);
        refresh = scm == null ? "" : scm.refresh();
        result = new StageConfiguration(mavenHome, templates().join("tomcat"), refresh);
        result.url = configuration.vhosts ? "(http|https)://%a.%s.%h:%p/" : "(http|https)://%h:%p/";
        configuration.setDefaults(accessors(), result, url);
        return result;
    }

    public String nextStageId() {
        nextStageId++;
        return stageIdPrefix + nextStageId;
    }

    private DefaultPlexusContainer lazyPlexus;

    public DefaultPlexusContainer plexus() {
        if (lazyPlexus == null) {
            lazyPlexus = Maven.container();
        }
        return lazyPlexus;
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

    public List<String> search(String search) throws IOException {
        FileNode working;
        List<String> cmd;
        List<String> result;
        int idx;

        working = world.getWorking();
        result = new ArrayList<>();
        if (configuration.search.isEmpty()) {
            throw new IOException("no search tool configured");
        }
        cmd = Separator.SPACE.split(configuration.search);
        idx = cmd.indexOf("()");
        if (idx == -1) {
            throw new IOException("search tool configured without () placeholder");
        }
        cmd.set(idx, search);
        for (String line : Separator.RAW_LINE.split(working.exec(Strings.toArray(cmd)))) {
            line = line.trim();
            result.add(line);
        }
        return result;
    }

    public void cd(FileNode dir) {
        if (setenv) {
            Setenv.get().cd(dir.getAbsolute());
        }
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
        for (FileNode stage : stageDirectories()) {
            config = loadStageConfiguration(Stage.backstageDirectory(stage));
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
}
