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
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.RmRfThread;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Cli;
import net.oneandone.sushi.cli.Command;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.cli.Value;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

/** Java installer. Creates a standalone stool directory with "bin" and "man" included. */
public class JavaSetup extends Cli implements Command {
    public static void main(String[] args) throws Exception {
        System.exit(new JavaSetup().run(args));
    }

    public static void standalone(Console console, boolean withJar, FileNode home, String config) throws Exception {
        RmRfThread cleanup;
        FileNode bin;

        home.checkNotExists();
        cleanup = new RmRfThread(console);
        cleanup.add(home);
        Runtime.getRuntime().addShutdownHook(cleanup);
        home(console, home, config).create();
        bin = home.join("bin");
        BinMan.java(console, withJar, bin, home.join("man")).create();
        bin.join("home").mklink(home.getAbsolute());
        // ok, no exceptions - we have a proper install directory: no cleanup
        Runtime.getRuntime().removeShutdownHook(cleanup);
    }


    //--

    @Value(name = "home", position = 1)
    private FileNode home;


    @Option("batch")
    private boolean batch;

    private String config;

    private final Environment environment;

    private JavaSetup() {
        environment = Environment.loadSystem();
    }

    @Remaining
    public void remaining(String str) throws IOException {
        if (config != null) {
            throw new ArgumentException("duplicate initialConfig: " + str + " vs " + config);
        }
        config = str;
    }

    @Override
    public void printHelp() {
        console.info.println("Setup stool " + versionObject());
        console.info.println("usage: setup-stool <home> [<json>]");
        console.info.println("  Create a new <home> directory or upgrades an existing.");
        console.info.println("  Does not modify anything outside the home directory.");
        console.info.println("documentation:");
        console.info.println("  https://github.com/mlhartme/stool");
    }

    @Override
    public void invoke() throws Exception {
        BinMan bm;

        environment.setStoolBin(home.join("bin"));
        if (home.exists()) {
            if (!batch) {
                console.info.println("Ready to upgrade " + home.getAbsolute() + " to Stool " + versionObject());
                console.pressReturn();
            }
            home(console, home, config).upgrade();
            bm = BinMan.java(console, true, home.join("bin"), home.join("man"));
            bm.remove();
            bm.create();
            console.info.println("Done. To complete the installation:");
            console.info.println("1. change your ~/.bashrc to");
            console.info.println("       source " + home.join("bin/stool-function").getAbsolute());
            console.info.println("2. restart your shell");
        } else {
            if (!batch) {
                console.info.println("Ready to install Stool " + versionObject() + " to " + home.getAbsolute());
                console.pressReturn();
            }
            standalone(console, true, home, config);
            console.info.println("Done. To complete the installation:");
            console.info.println("1. add");
            console.info.println("       source " + home.join("bin/stool-function").getAbsolute());
            console.info.println("   to your ~/.bashrc");
            console.info.println("2. restart your shell");
        }
    }

    public static Version versionObject() {
        String str;

        str = JavaSetup.class.getPackage().getSpecificationVersion();
        return Version.valueOf(str);
    }

    private static Home home(Console console, FileNode home, String config) throws IOException {
        return new Home(console, home, group(console.world), config);
    }

    private static String group(World world) throws IOException {
        FileNode file;
        String result;

        file = world.getTemp().createTempFile();
        result = file.getGroup().toString();
        file.deleteFile();
        return result;
    }
}
