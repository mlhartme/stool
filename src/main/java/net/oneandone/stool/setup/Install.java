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

import net.oneandone.stool.Overview;
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Logging;
import net.oneandone.stool.util.RmRfThread;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;


public class Install {
    // false in tests, when stool.jar is not in classpath
    private final boolean withJar;

    private final Console console;

    private final Environment environment;

    private final FileNode home;

    private final Map<String, Object> globalProperties;

    public Install(boolean withJar, Console console, Environment environment, Map<String, Object> globalProperties) {
        this.withJar = withJar;
        this.console = console;
        this.environment = environment;
        this.home = environment.stoolHome(console.world);
        this.globalProperties = globalProperties;
    }

    public Session invoke(String user) throws Exception {
        Session session;

        createHome();
        session = Session.load(null /* TODO */, user, "install", environment, console, null);
        Overview.createOverview(session);
        return session;
    }

    private void createHome() throws IOException {
        StoolConfiguration conf;
        RmRfThread cleanup;

        home.checkNotExists();

        cleanup = new RmRfThread(console);
        cleanup.add(home);
        Runtime.getRuntime().addShutdownHook(cleanup);

        home.mkdirs();
        conf = new StoolConfiguration();
        tuneHostname(conf);
        tuneExplicit(conf);
        copyResources(variables(conf.javaHome()));
        conf.save(home);

        // ok, no exceptions - we have a proper install directory: no cleanup
        Runtime.getRuntime().removeShutdownHook(cleanup);
    }

    private void copyResources(Map<String, String> variables) throws IOException {
        Files.template(home.getWorld().resource("templates/stool"), home, variables);
        if (withJar) {
            console.world.locateClasspathItem(getClass()).copyFile(home.join("bin/stool.jar"));
        }
        // manually create empty subdirectories, because git doesn't know them
        for (String dir : new String[] {"wrappers", "downloads", "inbox", "logs", "service-wrapper", "service-wrapper/downloads", "sessions",
                "tomcat", "tomcat/downloads"}) {
            home.join(dir).mkdir();
        }
    }

    private Map<String, String> variables(String javaHome) {
        Map<String, String> result;

        result = new HashMap<>();
        result.put("stool.home", home.getAbsolute());
        result.put("java.home", javaHome);
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

        error = false;
        for (Map.Entry<String, Object> entry : globalProperties.entrySet()) {
            try {
                conf.configure(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                console.info.println("invalid value for property " + entry.getKey() + " : " + e.getMessage());
                e.printStackTrace(console.verbose);
                error = true;
            }
        }
        if (error) {
            throw new ArgumentException("invalid configuration");
        }
    }
}
