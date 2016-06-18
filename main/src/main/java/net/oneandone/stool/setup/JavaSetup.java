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

import net.oneandone.inline.Cli;
import net.oneandone.inline.Console;
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.RmRfThread;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

/**
 * Creates an install directory (= "lib" + "bin" + "man")
 * Uses for integration tests and as Java installer for Mac OS.
 * And for Unix systems if you don't have (or don't want to use) sudo.
 */
public class JavaSetup {
    public static int doRun(String[] args) throws IOException {
        Console console;
        World world;
        Cli cli;

        world = World.create();
        console = Console.create();
        cli = new Cli();
        cli.primitive(FileNode.class, "file name", world.getWorking(), world::file);
        cli.begin(world);
          cli.begin(console, "-v -e  { setVerbose(v) setStacktraces(e) }");
            cli.add(JavaSetup.class, "ignored -batch config?=null");
        return cli.run(args);
    }

    public static void standalone(Console console, FileNode install, String config) throws Exception {
        RmRfThread cleanup;

        install.checkNotExists();
        cleanup = new RmRfThread(console);
        cleanup.add(install);
        Runtime.getRuntime().addShutdownHook(cleanup);
        Lib.withDefaultGroup(console, install, config).create();
        // ok, no exceptions - we have a proper install directory: no cleanup
        Runtime.getRuntime().removeShutdownHook(cleanup);
    }


    //--

    private final World world;
    private final FileNode directory;
    private boolean batch;

    /** Name of a json file with global config fragment. */
    private String config;

    private final Console console;
    private final Environment environment;

    public JavaSetup(World world, Console console, boolean batch, String config) throws IOException {
        this.world = world;
        this.console = console;
        this.batch = batch;
        this.directory = world.getHome().join(".stool");
        this.config = config == null ? null : subst(world.file(config).readString());
        this.environment = Environment.loadSystem();
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

    public void run() throws Exception {
        if (directory == null) {
            console.info.println("Setup stool " + versionString(world) + "\n"
                + "usage: setup-stool '-batch'? <config>?"
                + "  Create a new ~/.stool directory or upgrades an existing.\n"
                + "  Does not modify anything outside this directory."
                + "documentation:\n"
                + "  https://github.com/mlhartme/stool");
            return;
        }
        if (directory.exists()) {
            if (!batch) {
                console.info.println("Ready to upgrade " + directory.getAbsolute() + " to Stool " + versionString(directory.getWorld()));
                console.pressReturn();
            }
            Lib.withDefaultGroup(console, directory, config).upgrade("3.3.3");
            console.info.println("Done.");
        } else {
            if (!batch) {
                console.info.println("Ready to install Stool " + versionString(directory.getWorld()) + " to " + directory.getAbsolute());
                console.pressReturn();
            }
            standalone(console, directory, config);
            console.info.println("Done.");
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
