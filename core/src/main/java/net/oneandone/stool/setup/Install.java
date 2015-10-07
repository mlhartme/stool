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
import net.oneandone.sushi.io.OS;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;


/** Generates Stool install files. May be executed at build time (for Debian Installer) or runtime (Java Installer) */
public class Install {
    private final Console console;

    // to create bin directory with a stool jar. False in tests, when stool.jar is not in classpath
    private final boolean withJar;

    // configuration when installed on target system
    private final FileNode bin;
    private final FileNode man;

    public Install(Console console, boolean withJar, FileNode bin, FileNode man) {
        this.console = console;
        this.withJar = withJar;
        this.bin = bin;
        this.man = man;
    }

    public Session standalone(String user, Environment environment, FileNode home, Map<String, String> globalProperties) throws Exception {
        RmRfThread cleanup;
        Session session;

        home.checkNotExists();

        cleanup = new RmRfThread(console);
        cleanup.add(home);
        Runtime.getRuntime().addShutdownHook(cleanup);
        new Home(console, home, false, globalProperties).create();
        doCreateMan(man);
        doCreateBinWithoutHomeLink(bin);
        bin.join("home").mklink(home.getAbsolute());
        session = Session.load(Logging.forStool(home, user), user, "setup-stool", environment, console, null, null, null);
        // ok, no exceptions - we have a proper install directory: no cleanup
        Runtime.getRuntime().removeShutdownHook(cleanup);
        return session;
    }

    //--

    public void debianFiles(FileNode dest) throws Exception {
        dest.mkdir();
        doCreateBinWithoutHomeLink(dest.join(bin.getName()));
        doCreateMan(dest.join(man.getName()));
    }

    //--

    private void doCreateMan(FileNode destMan) throws IOException {
        Files.createStoolDirectory(console.verbose, destMan);
        console.world.resource("templates/man").copyDirectory(destMan);
        Files.stoolTree(console.verbose, destMan);
    }

    private void doCreateBinWithoutHomeLink(FileNode destBin) throws IOException {
        final byte[] marker = "exit $?\n".getBytes(Settings.UTF_8);
        Map<String, String> variables;
        byte[] bytes;
        int ofs;

        variables = new HashMap<>();
        variables.put("stool.bin", bin.getAbsolute());
        variables.put("man.path", "/usr/share/man".equals(man.getAbsolute()) ? "" :
                "# note that the empty entry instructs man to search locations.\n" +
                        "export MANPATH=" + man.getAbsolute() + ":$MANPATH\n");
        Files.createStoolDirectory(console.verbose, destBin);
        Files.template(console.verbose, console.world.resource("templates/bin"), destBin, variables);
        if (withJar) {
            // strip launcher from application file
            bytes = console.world.locateClasspathItem(getClass()).readBytes();
            ofs = indexOf(bytes, marker) + marker.length;
            try (OutputStream out = destBin.join("stool.jar").createOutputStream()) {
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
