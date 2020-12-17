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
package net.oneandone.stool.server.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.oneandone.stool.client.Client;
import net.oneandone.stool.client.PodConfig;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.registry.TagInfo;
import net.oneandone.stool.server.Main;
import net.oneandone.stool.server.Server;
import net.oneandone.stool.server.Stage;
import net.oneandone.stool.server.logging.AccessLogEntry;
import net.oneandone.stool.server.logging.DetailsLogEntry;
import net.oneandone.stool.server.users.User;
import net.oneandone.stool.server.util.Context;
import net.oneandone.stool.server.util.Info;
import net.oneandone.stool.server.util.PredicateParser;
import net.oneandone.stool.server.util.Validation;
import net.oneandone.stool.server.util.Value;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.util.Strings;

import javax.mail.MessagingException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LocalClient extends Client {
    private final Server server;

    public LocalClient(String context, Server server) {
        super(context);
        this.server = server;
    }

    public Engine engine() {
        return Engine.createLocal(server.context);
    }

    @Override
    public Map<String, Map<String, JsonElement>> list(String filter, List<String> select) throws IOException {
        Map<String, Map<String, JsonElement>> result;
        Context context;
        Map<String, IOException> problems;
        Map<String, JsonElement> obj;

        result = new HashMap<>();
        problems = new HashMap<>();
        try (Engine engine = engine()) {
            context = new Context(engine);
            for (Stage stage : server.list(engine, new PredicateParser(context).parse(filter), problems)) {
                obj = new HashMap<>();
                result.put(stage.getName(), obj);
                for (Info info : stage.fields()) {
                    if (select == null || select.remove(info.name())) {
                        obj.put(info.name(), info.getAsJson(context));
                    }
                }
                for (Value value : stage.values()) {
                    if (select != null && select.remove(value.name())) {
                        obj.put(value.name(), new JsonPrimitive(value.get(context)));
                    }
                }
                if (select != null && !select.isEmpty()) {
                    throw new IOException("select argument: unknown value/field(s): " + select);
                }
            }
            if (!problems.isEmpty()) {
                throw new IOException("nested problems: " + problems);
            }
            return result;
        }
    }

    @Override
    public Map<String, String> create(String name, String image, Map<String, String> values) throws IOException {
        Stage stage;

        try (Engine engine = engine()) {
            try {
                engine.helmRead(name);
                throw new FileAlreadyExistsException(name);
            } catch (FileNotFoundException e) {
                // OK, fall through
            }
            stage = Stage.create(engine, server, name, image, values);
            return stage.urlMap(new Context(engine).registry(stage));
        }
    }

    @Override
    public String publish(String name, String imageOpt, Map<String, String> values) throws IOException {
        String result;
        Stage stage;
        String imageOrRepository;

        try (Engine engine = engine()) {
            stage = server.load(engine, name);
            if (imageOpt != null) {
                imageOrRepository = imageOpt;
            } else {
                imageOrRepository = Registry.toRepository(stage.getImage());
            }
            result = stage.publish(imageOrRepository, values);
            return result;
        }
    }

    @Override
    public String version() throws IOException {
        return Main.versionString(World.create() /* TODO */);
    }

    @Override
    public void delete(String stage) throws IOException {
        try (Engine engine = engine()) {
            server.load(engine, stage).uninstall(engine);
        }
    }

    @Override
    public Map<String, String> getValues(String stage) throws IOException {
        Map<String, String> result;
        Context context;

        result = new HashMap<>();
        try (Engine engine = engine()) {
            context = new Context(engine);
            for (Value value : server.load(engine, stage).values()) {
                result.put(value.name(), disclose(value.name(), value.get(context)));
            }
            return result;
        }
    }

    // TODO: configurable
    private static final List<String> DISCLOSE = Strings.toList("cert", "fault");

    private static String disclose(String name, String value) {
        if (DISCLOSE.contains(name)) {
            return "(undisclosed)";
        } else {
            return value;
        }
    }

    @Override
    public Map<String, String> setValues(String name, Map<String, String> values) throws IOException {
        Stage stage;
        Value prop;
        String value;
        Context context;
        Map<String, String> clientValues;
        Map<String, String> result;

        try (Engine engine = engine()) {
            stage = server.load(engine, name);
            result = new HashMap<>();
            context = new Context(engine);
            clientValues = new HashMap<>();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                prop = stage.value(entry.getKey());
                value = entry.getValue();
                value = value.replace("{}", prop.get(context));
                clientValues.put(entry.getKey(), value);
                result.put(prop.name(), disclose(prop.name(), value));
            }
            stage.publish(null, clientValues);
            return result;
        }
    }

    @Override
    public List<String> validate(String stage, boolean email, boolean repair) throws IOException {
        List<String> output;

        try (Engine engine = engine()) {
            output = new Validation(server, engine).run(stage, email, repair);
        } catch (MessagingException e) {
            throw new IOException("email failure: " + e.getMessage(), e);
        }
        return output;
    }

    @Override
    public List<String> images(String name) throws IOException {
        Stage stage;
        List<TagInfo> all;
        TagInfo tagInfo;
        String marker;
        List<String> result;
        Registry registry;

        try (Engine engine = engine()) {
            result = new ArrayList<>();
            stage = server.load(engine, name);

            // TODO
            registry = stage.createRegistry(World.create() /* TODO */);
            all = stage.images(registry);

            tagInfo = stage.tagInfo(registry);
            for (TagInfo image : all) {
                marker = image.repositoryTag.equals(tagInfo.repositoryTag) ? "<==" : "";
                result.add(image.tag + "  " + marker);
                result.add("   id:            " + image.id);
                result.add("   repositoryTag: " + image.repositoryTag);
                result.add("   created-at:    " + image.createdAt);
                result.add("   created-by:    " + image.author);
                result.add("   labels:");
                for (Map.Entry<String, String> labels : image.labels.entrySet()) {
                    result.add("     " + labels.getKey() + "\t: " + labels.getValue());
                }
            }
            return result;
        }
    }

    @Override
    public Map<String, String> awaitAvailable(String name) throws IOException {
        Stage stage;
        Context context;

        try (Engine engine = engine()) {
            stage = server.load(engine, name);
            stage.awaitAvailable(engine);
            context = new Context(engine);
            return stage.urlMap(context.registry(stage));
        }
    }

    @Override
    public PodConfig podToken(String stage, int timeout) throws IOException {
        Collection<PodInfo> pods;
        PodInfo pod;
        String id;
        String saName;
        String roleName;
        String bindingName;

        if (timeout > 240) {
            throw new IOException("timeout to big: " + timeout);
        }
        currentWithPermissions(stage);
        try (Engine engine = engine()) {
            pods = server.load(engine, stage).runningPods(engine).values();
            if (pods.isEmpty()) {
                throw new IOException("no pods running for stage: " + stage);
            }
            pod = pods.iterator().next(); // TODO: how to choose different pod

            id = UUID.randomUUID().toString();
            saName = "sa-" + stage + "-" + id;
            roleName = "role-" + stage + "-" + id;
            bindingName = "binding-" + stage + "-" + id;

            engine.createServiceAccount(saName);
            engine.createRole(roleName, pod.name);
            engine.createBinding(bindingName, saName, roleName);

            return new PodConfig(server.settings.kubernetes,
                engine.getNamespace(), engine.getServiceAccountToken(saName), pod.name);
        }
    }

    // TODO: where to call
    private void schedulePodTokenCleanup(String saName, String roleName, String bindingName, int timeout) {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor();
        Runnable cleanup = new Runnable() {
            public void run() {
                try (Engine engine = Engine.createClusterOrLocal(server.context)) {
                    engine.deleteServiceAccount(saName);
                    engine.deleteRole(roleName);
                    engine.deleteBinding(bindingName);
                } catch (IOException e) {
                    e.printStackTrace(); // TODO: proper logging ...
                }
            }
        };
        ex.schedule(cleanup, timeout, TimeUnit.MINUTES);
    }

    private TagInfo currentWithPermissions(String stageName) throws IOException {
        Stage stage;
        TagInfo tagInfo;

        try (Engine engine = engine()) {
            stage = server.load(engine, stageName);
            tagInfo = stage.tagInfo(stage.createRegistry(World.create() /* TODO */));
        }
        server.checkFaultPermissions(User.authenticatedOrAnonymous().login, new ArrayList<>() /* TODO */);
        return tagInfo;
    }

    @Override
    public List<String> history(String stage, boolean details, int max) throws IOException {
        List<AccessLogEntry> entries;
        List<String> result;

        result = new ArrayList<>();
        try (Engine engine = engine()) {
            entries = server.load(engine, stage).accessLogAll(max);
            for (AccessLogEntry entry : entries) {
                result.add("[" + AccessLogEntry.DATE_FMT.format(entry.dateTime) + " " + entry.user + "] " + entry.clientCommand);
                if (details) {
                    for (DetailsLogEntry detail : server.detailsLog(entry.clientInvocation)) {
                        result.add("  " + detail.level + " " + detail.message);
                    }
                }
            }
        }
        return result;
    }
}
