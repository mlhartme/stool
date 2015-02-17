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

import net.oneandone.stool.EnumerationFailed;
import net.oneandone.stool.configuration.Bedroom;
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.users.User;
import net.oneandone.stool.users.UserNotFound;
import net.oneandone.stool.users.Users;
import net.oneandone.stool.setup.Install;
import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.ModeException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import javax.naming.NamingException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/** Mostly a representation of $STOOL_HOME */
public class Session {
    public static Session load(Logging logging, String user, String command, Environment environment, Console console, FileNode invocationFile) throws IOException {
        Session session;

        session = loadWithoutWipe(logging, user, command, environment, console, invocationFile);

        // my first thought was to watch for filesystem events to trigger wrapper wiping.
        // But there's quite a big delay and rmdif+mkdir is reported as modification. Plus the code is quite complex and
        // I don't know how to handle overflow events.
        // So I simple wipe thmm whenever I load stool home. That's a well-defined timing and that's before stool might
        // use a stale stage.
        session.wipeStaleWrappers();
        return session;
    }

    public void wipeStaleWrappers() throws IOException {
        long s;
        String pid;

        s = System.currentTimeMillis();
        for (FileNode wrapper : wrappers.list()) {
            if (wrapper.isDirectory()) {
                FileNode anchor = wrapper.join("anchor");
                if (!anchor.isDirectory() && anchor.isLink()) {
                    for (Node pidfile : wrapper.find("shared/run/*.pid")) {
                        pid = pidfile.readString().trim();
                        console.verbose.println("killing " + pid);
                        // TODO: sudo ...
                        new Launcher(wrapper, "kill", "-9", pid).execNoOutput();
                    }
                    console.verbose.println("stale wrapper detected: " + wrapper);
                    try {
                        wrapper.deleteTree();
                    } catch (IOException e) {
                        console.error.println(wrapper + ": cannot delete stale wrapper: " + e.getMessage());
                        e.printStackTrace(console.verbose);
                    }
                }
            }
        }
        console.verbose.println("wipeStaleWrappers done, ms=" + ((System.currentTimeMillis() - s)));
    }

    public static Session loadWithoutWipe(Logging logging, String user, String command, Environment environment, Console console, FileNode invocationFile) throws IOException {
        FileNode home;
        Session result;

        home = environment.stoolHome(console.world);
        home.checkDirectory();
        result = new Session(logging, user, command, home, console, environment, StoolConfiguration.load(home), Bedroom.loadOrCreate(home), invocationFile);
        result.selectedStageName = environment.get(Environment.STOOL_SELECTED);
        return result;
    }

    private static final int MEM_RESERVED_OS = 500;

    //--

    public final Logging logging;
    public final String user;
    public final String command;

    // TODO: redundant!
    public final FileNode home;
    public final Console console;
    public final Environment environment;
    public final StoolConfiguration stoolConfiguration;
    public final Bedroom bedroom;

    public final FileNode wrappers;


    /** may be null */
    private final FileNode invocationFile;
    private final Subversion subversion;

    private String selectedStageName;
    private Processes processes;
    public final Users users;

    public Session(Logging logging, String user, String command, FileNode home, Console console, Environment environment, StoolConfiguration stoolConfiguration,
                   Bedroom bedroom, FileNode invocationFile) {
        this.logging = logging;
        this.user = user;
        this.command = command;
        this.home = home;
        this.console = console;
        this.environment = environment;
        this.stoolConfiguration = stoolConfiguration;
        this.bedroom = bedroom;
        this.wrappers = home.join("wrappers");
        this.selectedStageName = null;
        this.invocationFile = invocationFile;
        this.subversion = new Subversion(null, null);
        if (stoolConfiguration.ldapUrl.isEmpty()) {
            this.users = Users.fromLogin();
        } else {
            this.users = Users.fromLdap(stoolConfiguration.ldapUrl, stoolConfiguration.ldapPrincipal, stoolConfiguration.ldapCredentials);
        }
    }

    //--

    public void saveConfiguration() throws IOException {
        stoolConfiguration.save(home);
    }


    public String jdkHome() {
        String result;

        result = System.getProperty("java.home");
        result = Strings.removeRightOpt(result, "/");
        return Strings.removeRightOpt(result, "/jre");
    }

    public FileNode bin(String name) {
        return home.join("bin", name);
    }

    //-- environment handling

    public static int memTotal() throws IOException {
        long result;

        result = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalPhysicalMemorySize();
        return (int) (result / 1024 / 1024);
    }

    //--

    public List<Stage> list(EnumerationFailed problems, Predicate predicate) throws IOException {
        List<Stage> result;
        Stage stage;

        result = new ArrayList<>();
        for (FileNode wrapper : wrappers.list()) {
            try {
                stage = Stage.load(this, wrapper);

            } catch (IOException e) {
                problems.add(wrapper, e);
                continue;
            }
            if (predicate.matches(stage)) {
                result.add(stage);
            }
        }
        return result;
    }

    public void select(Stage selected) throws ModeException {
        if (selected == null) {
            throw new IllegalArgumentException();
        }
        selectedStageName = selected.getName();
        environment.setAll(environment(selected));
    }

    public void backupEnvironment() throws ModeException {
        String backupKey;
        String backupValue;

        for (String key : environment(null).keys()) {
            backupKey = Environment.backupKey(key);
            backupValue = environment.get(backupKey);
            if (backupValue != null) {
                throw new ArgumentException("session already opened (environment variable already defined: " + backupKey + ")");
            }
            environment.set(backupKey, environment.get(key));
        }
    }

    public void resetEnvironment() throws IOException {
        Environment reset;
        String backupKey;
        String backupValue;

        reset = environment(null);
        for (String key : reset.keys()) {
            backupKey = Environment.backupKey(key);
            backupValue = environment.get(backupKey);
            environment.set(key, backupValue);
            environment.set(backupKey, null);
        }
    }

    public void invocationFileUpdate() throws IOException {
        List<String> lines;

        lines = new ArrayList<>();
        for (String key : environment(null).keys()) {
            lines.add(environment.code(key));
            lines.add(environment.code(Environment.backupKey(key)));
        }
        if (invocationFile != null) {
            if (console.getVerbose()) {
                for (String line : lines) {
                    console.verbose.println("[env] " + line);
                }
            }
            invocationFile.writeLines(lines);
        }
    }

    public Subversion subversion() {
        return subversion;
    }
    public Stage load(String stageName) throws IOException {
        FileNode wrapper;

        wrapper = wrappers.join(stageName);
        return Stage.load(this, wrapper);
    }
    public List<String> stageNames() throws IOException {
        List<FileNode> files;
        List<String> result;

        files = wrappers.list();
        result = new ArrayList<>(files.size());
        for (FileNode file : files) {
            result.add(file.getName());
        }
        return result;
    }

    //-- Memory checks - all value in MB
    public String getSelectedStageName() {
        return selectedStageName;
    }

    public Environment environment(Stage stage) throws ModeException {
        Environment env;
        String stoolIndicator;
        String mavenOpts;

        if (stage == null) {
            mavenOpts = "";
        } else {
            mavenOpts = stage.config().mavenOpts;
            mavenOpts = mavenOpts.replace("@localRepository@", stage.localRepository().getAbsolute());
            mavenOpts = mavenOpts.replace("@proxyOpts@", environment.proxyOpts(false));
            mavenOpts = new Macros(stoolConfiguration.macros).replace(mavenOpts);
        }
        env = new Environment();
        env.set(Environment.STOOL_SELECTED, selectedStageName);
        // for pws and repositories:
        if (stage != null) {
            env.set(Environment.MACHINE, stage.getMachine());
        }
        // for pws:
        env.set(Environment.STAGE_HOST, stage != null ? stage.getName() + "." + stoolConfiguration.hostname : null);
        // not that both MAVEN and ANT use JAVA_HOME to locate their JVM - it's not necessary to add java to the PATH variable
        env.set(Environment.JAVA_HOME, stage != null ? stage.config().javaHome : null);
        env.set(Environment.MAVEN_OPTS, mavenOpts);
        // to avoid ulimit permission denied warnings on Ubuntu machines:
        if (stage == null) {
            stoolIndicator = "";
        } else {
            stoolIndicator = "\\[$(stoolIndicatorColor)\\]" + stage.getName() + "\\[\\e[m\\]";
        }
        env.set(Environment.PS1, Strings.replace(stoolConfiguration.prompt, "\\+", stoolIndicator));
        env.set(Environment.PWD, (stage == null ? ((FileNode) console.world.getWorking()) : stage.getDirectory()).getAbsolute());
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

        reserved = 0;
        for (FileNode wrapper : getWrappers()) {
            if (Stage.shared(wrapper).join("run/tomcat.pid").exists()) {
                stage = loadStageConfiguration(wrapper);
                reserved += stage.tomcatHeap;
                reserved += stage.tomcatPerm;
            }
        }
        return reserved;
    }

    public void checkDiskFree() {
        int free;
        int min;

        free = diskFree();
        min = stoolConfiguration.diskMin;
        if (free < min) {
            throw new ArgumentException("Disk almost full. Currently available " + free + " mb, required " + min + " mb.");
        }
    }

    /** @return Free disk space in partition used for stool home. CAUTION: not necessarily the partition used for stages. */
    public int diskFree() {
        return (int) (home.toPath().toFile().getUsableSpace() / 1024 / 1024);
    }

    public List<FileNode> getWrappers() throws IOException {
        List<FileNode> lst;

        lst = wrappers.list();
        Collections.sort(lst, new Comparator<Node>() {
            @Override
            public int compare(Node left, Node right) {
                return left.getName().compareTo(right.getName());
            }
        });
        return lst;
    }

    public User lookupUser(String login) throws NamingException, UserNotFound {
        if (!stoolConfiguration.security.isLocal()) {
            return users.byLogin(login);
        } else {
            return null;
        }
    }


    public void chown(Stage stage, String newOwner) throws Failure {
        new Launcher(home, "sudo", bin("stool-chown.sh").getAbsolute(),
          stage.wrapper.getName(), newOwner).exec(console.info);
    }

    /** session lock */
    public Lock lock() {
        return new Lock(user, home.join("stool.aquire"));
    }

    public boolean isSelected(Stage stage) {
        return stage.getName().equals(selectedStageName);
    }

    //--

    public void sudo(String... cmd) throws IOException {
        FileNode script;
        FileNode backup;

        script = bin("stool-apache.sh");
        backup = home.getWorld().getTemp().createTempFile();
        script.copyFile(backup);
        script.writeLines(
          "#!/bin/sh",
          Separator.SPACE.join(cmd));
        try {
            // NOTES
            // * sudo calls are usually logged in /var/log/auth.log, including the arguments
            // * why can I run sudo on the apache script - even without specifying a user?
            new Launcher((FileNode) home.getWorld().getWorking(), "sudo", script.getAbsolute(),
              "stop", "/opt/ui/opt/stages/admin/conf/httpd.conf").exec(console.info);
        } finally {
            backup.copyFile(script);
        }

    }

    public Processes getProcesses(boolean everytimeNew) throws Failure {
        if (null == this.processes | everytimeNew) {
            this.processes = new Processes(new Launcher((FileNode) home.getWorld().getWorking(), "ps", "aux").exec());
        }
        return this.processes;
    }

    //-- stage properties


    public void saveStageProperties(StageConfiguration stageConfiguration, Node wrapper) throws IOException {
        stageConfiguration.save(wrapper);
    }

    public StageConfiguration loadStageConfiguration(Node wrapper) throws IOException {
        return StageConfiguration.load(wrapper);
    }

    //-- stool properties

    public List<FileNode> stageDirectories() throws IOException {
        List<FileNode> result;

        result = new ArrayList<>();
        for (FileNode wrapper : getWrappers()) {
            result.add((FileNode) Stage.anchor(wrapper).resolveLink());
        }
        return result;
    }
}
