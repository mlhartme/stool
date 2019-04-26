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

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Diff;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/** Basically a server factory */
public class Globals {
    public static Globals create(World world) throws IOException {
        FileNode home;
        FileNode logRoot;

        home = locateHome(world);
        if (home.exists()) {
            logRoot = locateLogs(home);
        } else {
            logRoot = world.getTemp().createTempDirectory();
        }
        return new Globals(home, logRoot, world);
    }

    public static FileNode locateHome(World world) {
        String value;

        value = System.getenv( "STOOL_SERVER_HOME");
        if (value == null) {
            return world.getHome().join(".stool-server");
        } else {
            return world.file(value);
        }
    }

    public static FileNode locateLogs(FileNode home) {
        if (home.isDirectory()) {
            // stool is properly set up
            return home.join("logs");
        } else {
            // to run stool setup
            return home.getParent().join("stool-logs");
        }
    }


    public final FileNode home;
    private final FileNode logRoot;
    public final World world;

    public Globals(FileNode home, FileNode logRoot, World world) {
        this.home = home;
        this.logRoot = logRoot;
        this.world = world;
    }

    public Server server() throws IOException {
        Server server;

        setup(Main.versionString(world), home);

        server = Server.load(home, logRoot);
        server.checkVersion();
        return server;
    }


    //--

    public void setup(String version, FileNode home) throws IOException {
        String was;

        home.checkDirectory();
        if (home.list().isEmpty()) {
            Server.LOGGER.info("initializing server home " + home);
            initialize(home);
        } else {
            was = home.join("version").readString().trim();
            if (was.equals(version)) {
                Server.LOGGER.info("using server home: " + home);
            } else {
                Server.LOGGER.info("Updating server home " + was + " -> " + version + ": " + home);

                if (!Server.majorMinor(was).equals(Server.majorMinor(version))) {
                    throw new IOException("cannot update - migration needed: " + was + " -> " + version + ": " + home.getAbsolute());
                }
                update(version, home);
            }
        }
    }

    private void initialize(FileNode home) throws IOException {
        World world;

        home.checkExists();
        world = home.getWorld();
        world.resource("files/home").copyDirectory(home);
        for (String name : new String[]{"stages","certs"}) {
            home.join(name).mkdir();
        }
        home.join("version").writeString(Main.versionString(world));
    }

    private static final List<String> CONFIG = Strings.toList();

    private void update(String version, FileNode home) throws IOException {
        String was;
        FileNode fresh;
        FileNode dest;
        String path;
        String left;
        String right;
        int count;

        was = home.join("version").readString().trim();
        if (!Server.majorMinor(was).equals(Server.majorMinor(version))) {
            throw new IOException("cannot update - migration needed: " + was + " -> " + version + ": " + home.getAbsolute());
        }
        fresh = home.getWorld().getTemp().createTempDirectory();
        fresh.deleteDirectory();
        initialize(fresh);
        count = 0;
        for (FileNode src : fresh.find("**/*")) {
            if (!src.isFile()) {
                continue;
            }
            path = src.getRelative(fresh);
            if (CONFIG.contains(path)) {
                continue;
            }
            dest = home.join(path);
            left = src.readString();
            right = dest.readString();
            if (!left.equals(right)) {
                Server.LOGGER.info("U " + path);
                Server.LOGGER.info(Strings.indent(Diff.diff(right, left), "  "));
                dest.writeString(left);
                count++;
            }
        }
        fresh.deleteTree();
        Server.LOGGER.info("Done, " + count  + " file(s) updated.");
    }


    //--


    // TODO: dead?
    public int handleException(String command, Throwable throwable) {
        // TODO: inline should not throw InvocationTargetException ...
        if (throwable instanceof InvocationTargetException) {
            return handleException(command, ((InvocationTargetException) throwable).getTargetException());
        } else {
            if ((throwable instanceof RuntimeException) && (!(throwable instanceof ArgumentException))) {
                try {
                    server().reportException(command, "RuntimeException", throwable);
                } catch (IOException e) {
                    Server.LOGGER.error("failed to report runtine exception: " + e.getMessage(), e);
                }
            }
            return -1;
        }
    }
}
