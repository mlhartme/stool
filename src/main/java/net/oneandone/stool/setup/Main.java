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
package net.oneandone.stool.setup;

import com.github.zafarkhaja.semver.Version;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.oneandone.stool.SystemImport;
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.RmRfThread;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Cli;
import net.oneandone.sushi.cli.Command;
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.fs.CopyException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Main extends Cli implements Command {
    public static void main(String[] args) throws Exception {
        System.exit(new Main().run(args));
    }

    private FileNode home;
    private FileNode oldHome;
    private final Map<String, String> config;

    private Main() {
        config = fromEnvironment();
    }

    public static Map<String, String> fromEnvironment() {
        final String prefix = "SETUP_STOOL_";
        String key;
        Map<String, String> result;

        result = new HashMap<>();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            key = entry.getKey();
            if (key.startsWith(prefix)) {
                result.put(key.substring(prefix.length()), entry.getValue());
            }
        }
        return result;
    }

    @Remaining
    public void remaining(String str) {
        int idx;

        if (home == null) {
            home = console.world.file(str);
        } else if (oldHome == null) {
            oldHome = home;
            home = console.world.file(str);
        } else {
            idx = str.indexOf('=');
            if (idx == -1) {
                throw new ArgumentException("key=value expected, got " + str);
            }
            config.put(str.substring(0, idx).trim(), str.substring(idx + 1).trim());
        }
    }

    @Override
    public void printHelp() {
        console.info.println("Setup stool " + version());
        console.info.println("usage: setup-stool [<old>] <home>");
        console.info.println("  Create a new <home> directory, upgrades an existing <home> (incremental upgrade), ");
        console.info.println("  or upgrade and existing <old> home directory into a new <home> (full upgrade).");
        console.info.println("  Does not modify anything outside the home directory.");
        console.info.println("documentation:");
        console.info.println("  https://github.com/mlhartme/stool");
    }

    @Override
    public void invoke() throws Exception {
        Version version;
        Version old;
        Environment environment;

        version = StoolConfiguration.version();
        if (home == null) {
            printHelp();
            return;
        }
        if (oldHome == null) {
            oldHome = home;
        } else {
            oldHome.checkDirectory();
        }
        environment = Environment.loadSystem();
        environment.setStoolHome(home);
        if (oldHome.exists()) {
            old = oldVersion();
            if (oldHome.equals(home)) {
                if (old.getMajorVersion() != version.getMajorVersion() || old.getMinorVersion() != version.getMinorVersion()) {
                    throw new ArgumentException("incremental upgrade " + old + " -> " + version
                            + " not possible, specify a new home to perform a full upgrade.");
                }
                incrementalUpgrade(old, version);
            } else {
                fullUpgrade(old, version, environment);
            }
        } else {
            console.info.println("Ready to install Stool " + version + " to " + home.getAbsolute());
            console.pressReturn();
            new Install(true, console, environment, config).invoke();
            console.info.println("Done. To complete the installation:");
            console.info.println("1. add");
            console.info.println("       source " + home.join("stool-function").getAbsolute());
            console.info.println("   to your ~/.bashrc");
            console.info.println("2. restart your shell");
        }
    }

    private void fullUpgrade(Version old, Version version, Environment environment) throws Exception {
        RmRfThread cleanup;
        Session session;

        console.info.println("Preparing full upgrade of " + home.getAbsolute() + ": " + old + " -> " + version);
        cleanup = new RmRfThread(console);
        cleanup.add(home);
        Runtime.getRuntime().addShutdownHook(cleanup);

        new Install(true, console, environment, config).invoke();
        session = Session.load(environment, console, null);
        new SystemImport(session, oldHome).invoke();

        Runtime.getRuntime().removeShutdownHook(cleanup);
        console.info.println("Done. To complete the installation:");
        console.info.println("1. change your ~/.bashrc to");
        console.info.println("       source " + home.join("stool-function").getAbsolute());
        console.info.println("2. restart your shell");
    }

    private void incrementalUpgrade(Version old, Version version) throws CopyException {
        FileNode jar;

        jar = home.join("bin/stool.jar");
        console.info.println("Ready for incremental upgrade of " + home.getAbsolute() + " from version " + old + " to " + version);
        console.info.println("M " + jar);
        console.pressReturn();
        console.world.locateClasspathItem(getClass()).copyFile(jar);
        console.info.println("done");
    }

    private Version oldVersion() throws IOException {
        Node file;
        JsonObject obj;

        file = oldHome.join("bin/stool.jar");
        if (file.exists()) {
            file = ((FileNode) file).openZip().join("META-INF/pominfo.properties");
            return Version.valueOf((String) file.readProperties().get("version"));
        } else {
            obj = (JsonObject) new JsonParser().parse(oldHome.join("config.json").readString());
            return Version.valueOf(obj.get("version").getAsString());
        }
    }
}
