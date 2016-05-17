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
import java.nio.file.Files;

/**
 * Creates an install directory (= "lib" + "bin" + "man")
 * Uses for integration tests and as Java installer for Mac OS.
 * And for Unix systems if you don't have (or don't want to use) sudo.
 */
public class JavaSetup extends Cli implements Command {
    public static void main(String[] args) {
        System.exit(new JavaSetup().run(args));
    }

    public static void standalone(Console console, boolean withJar, FileNode install, String config) throws Exception {
        RmRfThread cleanup;
        FileNode bin;

        install.checkNotExists();
        cleanup = new RmRfThread(console);
        cleanup.add(install);
        Runtime.getRuntime().addShutdownHook(cleanup);
        Lib.withDefaultGroup(console, install, config).create();
        bin = install.join("bin");
        BinMan.java(console, withJar, bin, install.join("man")).create();
        bin.join("lib").mklink(install.getAbsolute());
        // ok, no exceptions - we have a proper install directory: no cleanup
        Runtime.getRuntime().removeShutdownHook(cleanup);
    }


    //--

    /** Install directory */
    @Value(name = "directory", position = 1)
    private FileNode directory;


    @Option("batch")
    private boolean batch;

    /** Name of a json file with global config fragment. */
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
        config = subst(console.world.file(str).readString());
    }

    private static String subst(String str) {
        StringBuilder builder;
        String name;
        char c;

        builder = new StringBuilder(str.length());
        for (int i = 0, len = str.length(); i < len; i++) {
            c = str.charAt(i);
            switch (c) {
                case '$':
                    name = name(str, i + 1);
                    i += name.length();
                    builder.append(System.getenv(name));
                    break;
                default:
                    builder.append(c);
                    break;
            }
        }
        return builder.toString();
    }

    private static String name(String str, int start) {
        int c;
        int idx;


        idx = start;
        while (true) {
            c = str.charAt(idx);
            if (!Character.isAlphabetic(c) && c != '_') {
                return str.substring(start, idx);
            }
            idx++;
        }
    }

    @Override
    public void printHelp() {
        console.info.println("Setup stool " + versionString(console.world));
        console.info.println("usage: setup-stool <directory> [<json>]");
        console.info.println("  Create a new <directory> or upgrades an existing.");
        console.info.println("  Does not modify anything outside the <directory>.");
        console.info.println("documentation:");
        console.info.println("  https://github.com/mlhartme/stool");
    }

    @Override
    public void invoke() throws Exception {
        FileNode bin;
        FileNode binLib;
        BinMan bm;

        environment.setStoolBin(directory.join("bin"));
        if (directory.exists()) {
            if (!batch) {
                console.info.println("Ready to upgrade " + directory.getAbsolute() + " to Stool " + versionString(console.world));
                console.pressReturn();
            }
            Lib.withDefaultGroup(console, directory, config).upgrade();
            bin = directory.join("bin");
            binLib = bin.join("lib");
            bm = BinMan.java(console, true, bin, directory.join("man"));
            bm.remove();
            bm.create();
            if (Files.deleteIfExists(binLib.toPath())) {
                console.info.println("cleaned previous lib: " + binLib);
            }
            binLib.mklink(directory.getAbsolute());
            console.info.println("Done. To complete the installation:");
            console.info.println("1. change your ~/.bashrc to");
            console.info.println("       source " + directory.join("bin/stool-function").getAbsolute());
            console.info.println("2. restart your shell");
        } else {
            if (!batch) {
                console.info.println("Ready to install Stool " + versionString(console.world) + " to " + directory.getAbsolute());
                console.pressReturn();
            }
            standalone(console, true, directory, config);
            console.info.println("Done. To complete the installation:");
            console.info.println("1. add");
            console.info.println("       source " + directory.join("bin/stool-function").getAbsolute());
            console.info.println("   to your ~/.bashrc");
            console.info.println("2. restart your shell");
        }
    }


    public static String versionString(World world) {
        // don't use class.getPackage().getSpecificationVersion() because META-INF/META.MF
        // 1) is not available in Webapps (in particular: dashboard)
        // 2) is not available in test cases
        try {
            return world.resource("stool.version").readString().trim();
        } catch (IOException e) {
            throw new IllegalStateException("cannot determine version", e);
        }
    }
}
