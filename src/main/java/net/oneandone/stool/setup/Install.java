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
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

        doCreateHomeWithoutOverview(home);
        doCreateBinWithoutHomeLink(variables(), bin);
        bin.join("home").mklink(home.getAbsolute());
        doCreateMan(man);
        session = doCreateHomeOverview(user, environment, home);
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
            home.join("overview").deleteTree();
        } else {
            doCreateHomeWithoutOverview(home);
        }
        doCreateHomeOverview(user, environment, home);
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

    private void doCreateHomeWithoutOverview(FileNode home) throws IOException {
        StoolConfiguration conf;

        Files.stoolDirectory(home.mkdirs());
        conf = new StoolConfiguration(downloadCache(home));
        tuneHostname(conf);
        tuneExplicit(conf);
        Files.stoolDirectory(conf.downloadCache.mkdirOpt()).join(STOOL_UPDATE_CHECKED).deleteFileOpt().mkfile();
        conf.save(Session.gson(home.getWorld(), ExtensionsFactory.create(home.getWorld())), home);

        for (String dir : new String[]{"extensions", "backstages", "inbox", "logs", "service-wrapper", "run", "run/users", "tomcat"}) {
            Files.stoolDirectory(home.join(dir).mkdir());
        }
    }

    private Session doCreateHomeOverview(String user, Environment environment, FileNode home) throws IOException {
        Session session;

        session = Session.load(Logging.forStool(home, user), user, "setup-stool", environment, console, null, null, null);
        createOverview(session);
        return session;
    }

    private void doCreateBinWithoutHomeLink(Map<String, String> variables, FileNode destBin) throws IOException {
        final byte[] marker = "exit $?\n".getBytes("utf8");
        byte[] bytes;
        int ofs;

        Files.stoolDirectory(destBin.mkdir());
        Files.template(console.world.resource("templates/bin"), destBin, variables);
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
        Files.stoolDirectory(destMan.mkdir());
        console.world.resource("templates/man").copyDirectory(destMan);
        Files.stoolTree(destMan);
    }

    private Map<String, String> variables() {
        Map<String, String> result;

        result = new HashMap<>();
        result.put("stool.bin", bin.getAbsolute());
        result.put("man.path", "/usr/share/man".equals(man.getAbsolute()) ? "" :
                "if [ -z $MANPATH ] ; then\n" +
                "  export MANPATH=" + man.getAbsolute() + "\n" +
                "else\n" +
                "  export MANPATH=" + man.getAbsolute() + ":$MANPATH\n" +
                "fi\n");
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

    public static void createOverview(Session session) throws IOException {
        Create create;
        String url;
        String tomcatOpts;
        StageConfiguration stageConfiguration;

        stageConfiguration = session.createStageConfiguration("");
        url = "gav:overview:overview:@overview";
        create = new Create(session, true, Stage.OVERVIEW_NAME, url, overviewDirectory(session), stageConfiguration);
        tomcatOpts = session.createStageConfiguration(url).tomcatOpts;
        if (!tomcatOpts.isEmpty()) {
            tomcatOpts += " ";
        }
        tomcatOpts += "-Doverview.stool.bin=" + session.bin.getAbsolute();
        create.remaining("tomcat.opts=" + tomcatOpts);
        create.remaining("until=reserved");
        create.remaining("tomcat.env=" + environment());
        try {
            create.doInvoke();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    // make sure the overview sees all environment variables, because the build command expects this environment
    private static String environment() {
        List<String> keys;

        keys = new ArrayList<>(System.getenv().keySet());
        Collections.sort(keys);
        return Separator.COMMA.join(keys);
    }

    private static FileNode overviewDirectory(Session session) {
        return session.home.join(Stage.OVERVIEW_NAME);
    }

}
