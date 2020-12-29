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
import net.oneandone.stool.Main;
import net.oneandone.stool.server.api.StageNotFoundException;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.server.users.UserManager;
import net.oneandone.stool.util.Predicate;
import net.oneandone.sushi.fs.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        configuration = Configuration.load(Configuration.scYaml(world));
        configuration.validate();
        LOGGER.info("version: " + Main.versionString(world));
        LOGGER.info("context: " + context);
        LOGGER.info("configuration: " + configuration);
        return new Server(context, configuration);
    }

    //--

    public final String context;

    public final Configuration configuration;

    public final UserManager userManager;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public Server(String context, Configuration configuration) throws IOException {
        this.context = context;
        this.configuration = configuration;
        this.userManager = UserManager.loadOpt(configuration.lib.mkdirsOpt().join("users.json"));
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
}
