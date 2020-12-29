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
package net.oneandone.stool.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.Main;
import net.oneandone.stool.server.api.StageNotFoundException;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.server.users.UserManager;
import net.oneandone.stool.util.Predicate;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Immutable. */
public class Server {
    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);

    public static Server createLocal(World world, String context) throws IOException {
        return create(world, context);
    }

    public static Server createCluster(World world) throws IOException {
        return create(world, null);
    }

    private static Server create(World world, String context) throws IOException {
        Configuration configuration;
        String version;
        Server server;
        String localhostIp;
        boolean openShift;

        version = Main.versionString(world);
        configuration = Configuration.load(Configuration.scYaml(world));
        configuration.validate();
        LOGGER.info("version: " + version);
        LOGGER.info("context: " + context);
        LOGGER.info("configuration: " + configuration);
        try (Engine engine = Engine.createClusterOrLocal(context)) {
            openShift = engine.isOpenShift();
            LOGGER.info("OpenShift: " + openShift);
            localhostIp = InetAddress.getByName("localhost").getHostAddress();
            LOGGER.info("localhostIp: " + localhostIp);
            return new Server(context, world, openShift, localhostIp, configuration);
        }
    }

    //--

    public final String context;

    /** CAUTION: not thread safe! Try to use engine.world instead */
    private final World world;

    public final boolean openShift;

    /** Logs of stages. CAUTION: not thread safe! */
    private final FileNode stageLogs;

    public final String localhostIp;

    public final Configuration configuration;

    public final UserManager userManager;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public Server(String context, World world, boolean openShift, String localhostIp, Configuration configuration) throws IOException {
        this.context = context;
        this.world = world;
        this.openShift = openShift;
        this.stageLogs = world.file(configuration.stageLogs).mkdirsOpt();
        this.localhostIp = localhostIp;
        this.configuration = configuration;
        this.userManager = UserManager.loadOpt(configuration.lib.mkdirsOpt().join("users.json"));
    }

    public String stageFqdn(String stage) {
        return stage + "." + configuration.fqdn;
    }

    public FileNode getStageLogs(String name) {
        return stageLogs.join(name);
    }

    //-- Stage access

    public List<Stage> list(Engine engine, Predicate predicate, Map<String, IOException> problems) throws IOException {
        List<Stage> result;
        Stage stage;

        result = new ArrayList<>();
        for (String name : list(engine)) {
            try {
                stage = load(engine, name);
            } catch (IOException e) {
                e.printStackTrace();
                problems.put(name, e);
                continue;
            }
            if (predicate.matches(stage)) {
                result.add(stage);
            }
        }
        return result;
    }

    private static List<String> list(Engine engine) throws IOException {
        List<String> result;

        result = engine.helmList();
        result.remove("stool"); // optional, stool server is not required
        return result;
    }

    public List<Stage> listAll(Engine engine) throws IOException {
        List<Stage> result;
        Map<String, IOException> problems;

        problems = new HashMap<>();
        result = list(engine, new Predicate() {
            @Override
            public boolean matches(Stage stage) {
                return true;
            }
        }, problems);
        for (Map.Entry<String, IOException> entry : problems.entrySet()) {
            configuration.reportException("listAll" /* TODO */, entry.getKey() + ": Session.listAll", entry.getValue());
        }
        return result;
    }

    public Stage load(Engine engine, String name) throws IOException {
        String secretName;
        ObjectNode obj;

        secretName = engine.helmSecretName(name);
        try {
            obj = engine.helmSecretRead(secretName);
        } catch (FileNotFoundException e) {
            throw new StageNotFoundException(name);
        }
        return Stage.create(this, name, obj, Stage.historyFromMap(engine.secretGetAnnotations(secretName)));
    }

    //--

    public void checkFaultPermissions(String user, List<String> projects) throws IOException {
        Properties permissions;
        String lst;

        if (projects.isEmpty()) {
            return;
        }
        permissions = world.file("/etc/fault/workspace.permissions").readProperties();
        for (String project : projects) {
            lst = permissions.getProperty(project);
            if (lst == null) {
                throw new ArgumentException("fault project unknown or not accessible on this host: " + project);
            }
            if (!Separator.COMMA.split(lst).contains(user)) {
                throw new ArgumentException("fault project " + project + ": permission denied for user " + user);
            }
        }
    }
}
