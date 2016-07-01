/**
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
import net.oneandone.stool.configuration.Bedroom;
import net.oneandone.stool.configuration.Expire;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.configuration.adapter.ExpireTypeAdapter;
import net.oneandone.stool.configuration.adapter.ExtensionsAdapter;
import net.oneandone.stool.configuration.adapter.FileNodeTypeAdapter;
import net.oneandone.stool.extensions.ExtensionsFactory;
import net.oneandone.stool.locking.LockManager;
import net.oneandone.stool.scm.Scm;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.users.User;
import net.oneandone.stool.users.UserNotFound;
import net.oneandone.stool.users.Users;
import net.oneandone.sushi.fs.LinkException;
import net.oneandone.sushi.fs.ModeException;
import net.oneandone.sushi.fs.ReadLinkException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import org.codehaus.plexus.DefaultPlexusContainer;

import javax.mail.MessagingException;
import javax.naming.NamingException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Session {
    public static Session load(boolean setenv, FileNode lib, Logging logging, String user, String command, Console console, World world,
                               String svnuser, String svnpassword) throws IOException {
        Session session;

        session = loadWithoutBackstageWipe(setenv, lib, logging, user, command, console, world, svnuser, svnpassword);

        // Stale backstage wiping: how to detect backstage directories who's stage directory was removed.
        //
        // My first thought was to watch for filesystem events to trigger backstage wiping.
        // But there's quite a big delay and rmdir+mkdir is reported as modification. Plus the code is quite complex and
        // I don't know how to handle overflow events.
        // So I simple wipe them whenever I load stool lib. That's a well-defined timing and that's before stool might
        // use a stale stage.
        session.wipeStaleBackstages();
        return session;
    }

    public void wipeStaleBackstages() throws IOException {
        long s;

        s = System.currentTimeMillis();
        for (FileNode backstage : backstages.list()) {
            if (backstage.exists() && !backstage.isFile() && !backstage.isDirectory()) {
                console.info.println("removing stale backstage: " + backstage);
                backstage.deleteTree();
            }
        }
        console.verbose.println("wipeStaleBackstages done, ms=" + ((System.currentTimeMillis() - s)));
    }

    private static Session loadWithoutBackstageWipe(boolean setenv, FileNode lib, Logging logging, String user, String command,
                                                    Console console,
                                                  World world, String svnuser, String svnpassword) throws IOException {
        ExtensionsFactory factory;
        Gson gson;
        Session result;
        Environment environment;

        factory = ExtensionsFactory.create(world);
        gson = gson(world, factory);
        environment = Environment.loadSystem();
        result = new Session(setenv, factory, gson, logging, user, command, lib, console, world, environment,
                StoolConfiguration.load(gson, lib), Bedroom.loadOrCreate(gson, lib), svnuser, svnpassword);
        return result;
    }

    private static final int MEM_RESERVED_OS = 500;

    //--

    private final boolean setenv;
    public final ExtensionsFactory extensionsFactory;
    public final Gson gson;
    public final Logging logging;
    public final String user;
    private final String command;

    public final FileNode lib;
    private String lazyGroup;

    public final Console console;
    public final World world;
    public final Environment environment;
    public final StoolConfiguration configuration;
    public final Bedroom bedroom;

    private final FileNode backstages;

    private final Credentials svnCredentials;

    private final String stageIdPrefix;
    private int nextStageId;
    public final Users users;
    public final LockManager lockManager;

    private Pool lazyPool;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyMMdd");

    public Session(boolean setenv, ExtensionsFactory extensionsFactory, Gson gson, Logging logging, String user, String command,
                   FileNode lib, Console console, World world, Environment environment, StoolConfiguration configuration,
                   Bedroom bedroom, String svnuser, String svnpassword) {
        this.setenv = setenv;
        this.extensionsFactory = extensionsFactory;
        this.gson = gson;
        this.logging = logging;
        this.user = user;
        this.command = command;
        this.lib = lib;
        this.lazyGroup = null;
        this.console = console;
        this.world = world;
        this.environment = environment;
        this.configuration = configuration;
        this.bedroom = bedroom;
        this.backstages = lib.join("backstages");
        this.svnCredentials = new Credentials(svnuser, svnpassword);
        this.stageIdPrefix = FMT.format(LocalDate.now()) + "." + logging.id + ".";
        this.nextStageId = 0;
        if (configuration.ldapUrl.isEmpty()) {
            this.users = Users.fromLogin();
        } else {
            this.users = Users.fromLdap(configuration.ldapUrl, configuration.ldapPrincipal, configuration.ldapCredentials);
        }
        this.lockManager = LockManager.create(lib.join("run/locks"), user + ":" + command, 10);
        this.lazyPool= null;
    }

    public void add(FileNode backstage, String id) throws LinkException {
        backstage.link(backstages.join(id));
    }

    public FileNode backstageLink(String id) throws ReadLinkException {
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

    //--

    public String group() throws ModeException {
        if (lazyGroup == null) {
            lazyGroup = lib.getGroup().toString();
        }
        return lazyGroup;
    }

    public FileNode bin(String name) {
        return lib.join("bin", name);
    }

    //-- environment handling

    public static int memTotal() {
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
                    problems.add(backstage, e);
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
        for (Map.Entry<FileNode, Exception> entry : problems.problems.entrySet()) {
            reportException(entry.getKey() + ": Session.listWithoutDashboard", entry.getValue());
        }
        return result;
    }

    public Scm scm(String url) {
        return Scm.forUrl(url, svnCredentials);
    }

    public Credentials svnCredentials() {
        return svnCredentials;
    }

    public Stage load(String id) throws IOException {
        return Stage.load(this, backstages.join(id));
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
            }
        }
        return lazySelectedId;
    }

    public Environment environment(Stage stage) {
        Environment env;
        String mavenOpts;

        if (stage == null) {
            mavenOpts = "";
        } else {
            mavenOpts = stage.macros().replace(stage.config().mavenOpts);
        }
        env = new Environment();
        // note that both MAVEN and ANT use JAVA_HOME to locate their JVM - it's not necessary to add java to the PATH variable
        env.set(Environment.JAVA_HOME, stage != null ? stage.config().javaHome : null);
        env.set(Environment.MAVEN_HOME, (stage != null && stage.config().mavenHome() != null) ? stage.config().mavenHome() : null);
        env.set(Environment.MAVEN_OPTS, mavenOpts);
        return env;
    }

    //--

    /** @return memory not yet reserved */
    public int memUnreserved() throws IOException {
        return memTotal() - MEM_RESERVED_OS - memReservedTomcats();
    }

    /** used for running tomcat */
    private int memReservedTomcats() throws IOException {
        int reserved;
        StageConfiguration stage;
        FileNode backstage;

        reserved = 0;
        for (FileNode link : backstages.list()) {
            backstage = link.resolveLink();
            if (Stage.shared(backstage).join("run/tomcat.pid").exists()) {
                stage = loadStageConfiguration(backstage);
                reserved += stage.tomcatHeap;
            }
        }
        return reserved;
    }

    public void checkDiskFree() {
        int free;
        int min;

        free = diskFree();
        min = configuration.diskMin;
        if (free < min) {
            throw new ArgumentException("Disk almost full. Currently available " + free + " mb, required " + min + " mb.");
        }
    }

    /** @return Free disk space in partition used for stool lib. TODO: not necessarily the partition used for stages. */
    public int diskFree() {
        return (int) (lib.toPath().toFile().getUsableSpace() / 1024 / 1024);
    }

    public User lookupUser(String login) throws NamingException, UserNotFound {
        if (configuration.shared) {
            return users.byLogin(login);
        } else {
            return null;
        }
    }


    public void chown(Stage stage, String newOwner) throws Failure {
        chown(newOwner, stage.getDirectory());
    }

    public void chown(String newOwner, FileNode ... dirs) throws Failure {
        Launcher launcher;

        launcher = new Launcher(lib, "sudo", bin("chowntree.sh").getAbsolute(), newOwner);
        for (FileNode dir : dirs) {
            launcher.arg(dir.getAbsolute());
        }
        launcher.exec(console.info);
    }

    public FileNode ports() {
        return lib.join("run/ports");
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

        try {
            mavenHome = Maven.locateMaven(world).getAbsolute();
        } catch (IOException e) {
            mavenHome = "";
        }
        result = new StageConfiguration(javaHome(), mavenHome, scm(url).refresh(), extensionsFactory.newInstance());
        configuration.setDefaults(StageConfiguration.properties(extensionsFactory), result, url);
        return result;
    }

    public String nextStageId() {
        nextStageId++;
        return stageIdPrefix + nextStageId;
    }

    public static String javaHome() {
        String result;

        result = System.getProperty("java.home");
        if (result == null) {
            throw new IllegalStateException();
        }
        result = Strings.removeRightOpt(result, "/");
        // prefer jdk, otherwise Java tools like jar while report an error on Mac OS ("unable to locate executable at $JAVA_HOME")
        result = Strings.removeRightOpt(result, "/jre");
        return result;
    }

    private DefaultPlexusContainer lazyPlexus;

    public DefaultPlexusContainer plexus() {
        if (lazyPlexus == null) {
            lazyPlexus = Maven.container();
        }
        return lazyPlexus;
    }

    public static Gson gson(World world, ExtensionsFactory factory) {
        return new GsonBuilder()
                .registerTypeAdapter(FileNode.class, new FileNodeTypeAdapter(world))
                .registerTypeAdapter(Expire.class, new ExpireTypeAdapter())
                .registerTypeAdapterFactory(ExtensionsAdapter.factory(factory))
                .disableHtmlEscaping()
                .serializeNulls()
                .excludeFieldsWithModifiers(Modifier.STATIC, Modifier.TRANSIENT)
                .setPrettyPrinting()
                .create();
    }

    public FileNode downloadCache() {
        return configuration.downloadCache;
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
            line = Strings.removeLeftOpt(line, "git:");
            line = Strings.removeLeftOpt(line, "svn:");
            result.add(line);
        }
        return result;
    }

    public void cd(FileNode dir) {
        if (setenv) {
            Setenv.get().cd(dir.getAbsolute());
        }
    }
}
