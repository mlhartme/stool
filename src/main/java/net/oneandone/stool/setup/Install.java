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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Install {
    public static final String STOOL_UPDATE_CHECKED = ".stool.update.checked";

    // false in tests, when stool.jar is not in classpath
    private final boolean fromJar;

    private final Console console;

    private final Environment environment;

    private final FileNode home;

    private final FileNode bin;

    private final FileNode man;

    private final boolean withBin;

    private final boolean withMan;

    private final Map<String, Object> globalProperties;

    public Install(boolean fromJar, Console console, Environment environment, Map<String, Object> globalProperties) {
        this(fromJar, console, environment,
                environment.stoolHome(console.world).join("man"), true, true,
                globalProperties);
    }

    public Install(boolean fromJar, Console console, Environment environment, FileNode man, boolean withBin, boolean withMan,
                   Map<String, Object> globalProperties) {
        this.fromJar = fromJar;
        this.console = console;
        this.environment = environment;
        this.home = environment.stoolHome(console.world);
        this.bin = environment.stoolBin(console.world);
        this.man = man;
        this.withBin = withBin;
        this.withMan = withMan;
        this.globalProperties = globalProperties;
    }

    public Session invoke(String user) throws Exception {
        Session session;

        createHome();
        session = Session.load(Logging.forStool(home, user), user, "setup-stool", environment, console, null, null, null);
        createOverview(session);
        return session;
    }

    private void createHome() throws IOException {
        StoolConfiguration conf;
        RmRfThread cleanup;

        home.checkNotExists();

        cleanup = new RmRfThread(console);
        cleanup.add(home);
        Runtime.getRuntime().addShutdownHook(cleanup);

        Files.stoolDirectory(home.mkdirs());
        conf = new StoolConfiguration(downloadCache(home));
        tuneHostname(conf);
        tuneExplicit(conf);
        doCreateHome();
        if (withBin) {
            doCreateBin(variables(Session.javaHome()));
        }
        if (withMan) {
            doCreateMan();
        }
        Files.stoolDirectory(conf.downloadCache.mkdirOpt()).join(STOOL_UPDATE_CHECKED).deleteFileOpt().mkfile();
        conf.save(Session.gson(home.getWorld(), ExtensionsFactory.create(home.getWorld())), home);

        // ok, no exceptions - we have a proper install directory: no cleanup
        Runtime.getRuntime().removeShutdownHook(cleanup);
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

    public static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyMMdd-hhmmss");

    private void doCreateHome() throws IOException {
        for (String dir : new String[] { "extensions", "wrappers", "inbox", "logs", "service-wrapper", "conf", "conf/users", "tomcat"}) {
            Files.stoolDirectory(home.join(dir).mkdir());
        }
    }

    private void doCreateBin(Map<String, String> variables) throws IOException {
        String jar;

        Files.stoolDirectory(bin.mkdir());
        Files.template(home.getWorld().resource("templates/bin"), bin, variables);
        if (fromJar) {
            jar = "stool-" + FMT.format(LocalDateTime.now()) + ".jar";
            console.world.locateClasspathItem(getClass()).copyFile(bin.join(jar));
            bin.join("stool.jar").mklink(jar);
        }
    }

    private void doCreateMan() throws IOException {
        Files.stoolDirectory(man.mkdir());
        home.getWorld().resource("templates/man").copyDirectory(man);
        Files.stoolTree(man);
    }

    private Map<String, String> variables(String javaHome) {
        Map<String, String> result;

        result = new HashMap<>();
        result.put("stool.home", home.getAbsolute());
        result.put("stool.bin", bin.getAbsolute());
        result.put("java.home", javaHome);
        result.put("man.path", man == null ? "" :
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
        tomcatOpts += "-Doverview.stool.home=" + session.home.getAbsolute();
        tomcatOpts += " -Doverview.stool.bin=" + session.bin.getAbsolute();
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
