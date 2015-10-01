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

import net.oneandone.stool.Create;
import net.oneandone.stool.configuration.Property;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.extensions.ExtensionsFactory;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Logging;
import net.oneandone.stool.util.RmRfThread;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.Settings;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;


public class Install {
    public static final String STOOL_UPDATE_CHECKED = ".stool.update.checked";

    private final Console console;

    // to create bin directory with a stool jar. False in tests, when stool.jar is not in classpath
    private final boolean withJar;

    // configuration when installed on target system
    private final FileNode bin;
    private final FileNode man;

    private final Map<String, Object> globalProperties;

    public Install(Console console, boolean withJar, FileNode bin, FileNode man, Map<String, Object> globalProperties) {
        this.console = console;
        this.withJar = withJar;
        this.bin = bin;
        this.man = man;
        this.globalProperties = globalProperties;
    }

    public Session standalone(String user, Environment environment, FileNode home) throws Exception {
        RmRfThread cleanup;
        Session session;

        home.checkNotExists();

        cleanup = new RmRfThread(console);
        cleanup.add(home);
        Runtime.getRuntime().addShutdownHook(cleanup);

        doCreateHomeWithoutDashboard(home, false);
        doCreateBinWithoutHomeLink(variables(), bin);
        bin.join("home").mklink(home.getAbsolute());
        doCreateMan(man);
        session = doCreateHomeDashboard(user, environment, home);
        // ok, no exceptions - we have a proper install directory: no cleanup
        Runtime.getRuntime().removeShutdownHook(cleanup);
        return session;
    }

    public void debianFiles(FileNode dest) throws Exception {
        dest.mkdir();
        doCreateBinWithoutHomeLink(variables(), dest.join(bin.getName()));
        doCreateMan(dest.join(man.getName()));
    }

    public void debianHome(String user, Environment environment, FileNode home) throws Exception {
        if (home.exists()) {
            home.join("dashboard").deleteTree();
        } else {
            doCreateHomeWithoutDashboard(home, true);
        }
        doCreateHomeDashboard(user, environment, home);
    }

    private FileNode downloadCache(FileNode home) {
        FileNode directory;

        if (OS.CURRENT == OS.MAC) {
            directory = (FileNode) console.world.getHome().join("Downloads");
            if (directory.isDirectory()) {
                return directory;
            }
        }
        return home.join("downloads");
    }

    private void doCreateHomeWithoutDashboard(FileNode home, boolean shared) throws IOException {
        StoolConfiguration conf;

        home.getParent().mkdirsOpt();
        Files.createStoolDirectory(console.verbose, home);
        conf = new StoolConfiguration(downloadCache(home));
        conf.shared = shared;
        tuneHostname(conf);
        tuneExplicit(conf);
        Files.createStoolDirectoryOpt(console.verbose, conf.downloadCache).join(STOOL_UPDATE_CHECKED).deleteFileOpt().mkfile();
        conf.save(Session.gson(home.getWorld(), ExtensionsFactory.create(home.getWorld())), home);

        for (String dir : new String[]{"extensions", "backstages", "inbox", "logs", "service-wrapper", "run", "run/users", "tomcat"}) {
            Files.createStoolDirectory(console.verbose, home.join(dir));
        }
    }

    private Session doCreateHomeDashboard(String user, Environment environment, FileNode home) throws IOException {
        Session session;

        session = Session.load(Logging.forStool(home, user), user, "setup-stool", environment, console, null, null, null);
        // TODO: createDashboard(session);
        return session;
    }

    private void doCreateBinWithoutHomeLink(Map<String, String> variables, FileNode destBin) throws IOException {
        final byte[] marker = "exit $?\n".getBytes(Settings.UTF_8);
        byte[] bytes;
        int ofs;

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

    private void doCreateMan(FileNode destMan) throws IOException {
        Files.createStoolDirectory(console.verbose, destMan);
        console.world.resource("templates/man").copyDirectory(destMan);
        Files.stoolTree(console.verbose, destMan);
    }

    private Map<String, String> variables() {
        Map<String, String> result;

        result = new HashMap<>();
        result.put("stool.bin", bin.getAbsolute());
        result.put("man.path", "/usr/share/man".equals(man.getAbsolute()) ? "" :
                "# note that the empty entry instructs man to search locations.\n" +
                "export MANPATH=" + man.getAbsolute() + ":$MANPATH\n");
        return result;
    }

    private void tuneHostname(StoolConfiguration conf) {
        try {
            conf.hostname = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            console.info.println("WARNING: cannot configure hostname: " + e.getMessage() + ". Using " + conf.hostname);
        }
    }

    private void tuneExplicit(StoolConfiguration conf) {
        boolean error;
        Map<String, Property> properties;
        Property property;

        properties = StoolConfiguration.properties();
        error = false;
        for (Map.Entry<String, Object> entry : globalProperties.entrySet()) {
            property = properties.get(entry.getKey());
            if (property == null) {
                console.info.println("property not found: " + entry.getKey());
                error = true;
            } else {
                try {
                    property.set(conf, entry.getValue());
                } catch (Exception e) {
                    console.info.println("invalid value for property " + entry.getKey() + " : " + e.getMessage());
                    e.printStackTrace(console.verbose);
                    error = true;
                }
            }
        }
        if (error) {
            throw new ArgumentException("invalid configuration");
        }
    }

    //--

    public static void createDashboard(Session session) throws IOException {
        Create create;
        String url;
        StageConfiguration stageConfiguration;

        stageConfiguration = session.createStageConfiguration("");
        url = "gav:dashboard:dashboard:@dashboard";
        create = new Create(session, true, "dashboard", url, dashboardDirectory(session), stageConfiguration);
        create.remaining("tomcat.opts=" + session.createStageConfiguration(url).tomcatOpts);
        create.remaining("until=reserved");
        try {
            create.doInvoke();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static FileNode dashboardDirectory(Session session) {
        return session.home.join("dashboard");
    }

}
