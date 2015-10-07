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
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Logging;
import net.oneandone.stool.util.RmRfThread;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.Settings;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;


/** Generates man- and bin directories. Executed at build time (for Debian Installer) or runtime (Java Installer) */
public class ManBin {
    public static ManBin debian(Console console, boolean withJar, FileNode installedMan, FileNode installedBin, FileNode dest) {
        return new ManBin(console, withJar, installedMan, installedBin, dest.join(installedMan.getName()), dest.join(installedBin.getName()));
    }

    public static ManBin java(Console console, boolean withJar, FileNode installedMan, FileNode installedBin) {
        return new ManBin(console, withJar, installedMan, installedBin, installedMan, installedBin);
    }

    private final Console console;

    // to create bin directory with a stool jar. False in tests, when stool.jar is not in classpath
    private final boolean withJar;

    // locations when installed on target machine
    private final FileNode installedMan;
    private final FileNode installedBin;
    private final FileNode nowMan;
    private final FileNode nowBin;

    private ManBin(Console console, boolean withJar, FileNode installedMan, FileNode installedBin, FileNode nowMan, FileNode nowBin) {
        this.console = console;
        this.withJar = withJar;
        this.installedMan = installedMan;
        this.installedBin = installedBin;
        this.nowMan = nowMan;
        this.nowBin = nowBin;
    }

    public Session standalone(String user, Environment environment, FileNode home, Map<String, String> globalProperties) throws Exception {
        RmRfThread cleanup;
        Session session;

        home.checkNotExists();

        cleanup = new RmRfThread(console);
        cleanup.add(home);
        Runtime.getRuntime().addShutdownHook(cleanup);
        new Home(console, home, false, globalProperties).create();
        run();
        installedBin.join("home").mklink(home.getAbsolute());
        session = Session.load(Logging.forStool(home, user), user, "setup-stool", environment, console, null, null, null);
        // ok, no exceptions - we have a proper install directory: no cleanup
        Runtime.getRuntime().removeShutdownHook(cleanup);
        return session;
    }

    public void run() throws IOException {
        doCreateMan();
        doCreateBinWithoutHomeLink();
    }

    //--

    private void doCreateMan() throws IOException {
        Files.createStoolDirectory(console.verbose, nowMan);
        console.world.resource("templates/man").copyDirectory(nowMan);
        Files.stoolTree(console.verbose, nowMan);
    }

    private void doCreateBinWithoutHomeLink() throws IOException {
        final byte[] marker = "exit $?\n".getBytes(Settings.UTF_8);
        Map<String, String> variables;
        byte[] bytes;
        int ofs;

        variables = new HashMap<>();
        variables.put("stool.bin", installedBin.getAbsolute());
        variables.put("man.path", "/usr/share/man".equals(installedMan.getAbsolute()) ? "" :
                "# note that the empty entry instructs man to search locations.\n" +
                        "export MANPATH=" + installedMan.getAbsolute() + ":$MANPATH\n");
        Files.createStoolDirectory(console.verbose, nowBin);
        Files.template(console.verbose, console.world.resource("templates/bin"), nowBin, variables);
        if (withJar) {
            // strip launcher from application file
            bytes = console.world.locateClasspathItem(getClass()).readBytes();
            ofs = indexOf(bytes, marker) + marker.length;
            try (OutputStream out = nowBin.join("stool.jar").createOutputStream()) {
                out.write(bytes, ofs, bytes.length - ofs);
            }
        }
    }

    public static int indexOf(byte[] array, byte[] sub) {
        int j;

        for (int i = 0; i < array.length - sub.length; i++) {
            for (j = 0; j < sub.length; j++) {
                if (sub[j] != array[i + j]) {
                    break;
                }
            }
            if (j == sub.length) {
                return i;
            }
        }
        throw new IllegalStateException();
    }
}
