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
package net.oneandone.stool.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import net.oneandone.stool.core.Configuration;
import net.oneandone.stool.core.Field;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.registry.TagInfo;
import net.oneandone.stool.Main;
import net.oneandone.stool.core.Stage;
import net.oneandone.stool.core.HistoryEntry;
import net.oneandone.stool.server.users.User;
import net.oneandone.stool.util.PredicateParser;
import net.oneandone.stool.util.Validation;
import net.oneandone.stool.core.Value;
import net.oneandone.stool.helmclasses.Expressions;
import net.oneandone.sushi.fs.World;

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
    private final ObjectMapper json;

    /** null for cluster */
    private final String kubernetesContext;

    private final Configuration configuration;

    public LocalClient(String context, String kubernetesContext, Configuration configuration, Caller caller) {
        super(context, caller);
        this.json = new ObjectMapper();
        this.kubernetesContext = kubernetesContext;
        this.configuration = configuration;
    }

    public Engine engine() {
        return Engine.createClusterOrLocal(kubernetesContext);
    }

    @Override
    public Map<String, Map<String, JsonNode>> list(String filter, List<String> select, boolean hidden) throws IOException {
        Map<String, Map<String, JsonNode>> result;
        Map<String, IOException> problems;
        Map<String, JsonNode> s;
        List<String> remaining;

        result = new HashMap<>();
        problems = new HashMap<>();
        try (Engine engine = engine()) {
            for (Stage stage : configuration.list(engine, new PredicateParser(engine).parse(filter), problems)) {
                s = new HashMap<>();
                result.put(stage.getName(), s);
                remaining = new ArrayList<>(select);
                for (Field field : stage.fields()) {
                    if ((select.isEmpty() && (hidden || !field.hidden)) || remaining.remove(field.name())) {
                        s.put(field.name(), field.getAsJson(json, engine));
                    }
                }
                // add values explictly selected
                for (Value value : stage.values()) {
                    if (!select.isEmpty() && remaining.remove(value.name())) {
                        s.put(value.name(), new TextNode(value.disclose()));
                    }
                }
                if (!remaining.isEmpty()) {
                    throw new IOException("select argument: unknown value/field(s): " + remaining);
                }
            }
            if (!problems.isEmpty()) {
                throw new IOException("nested problems: " + problems);
            }
            return result;
        }
    }

    @Override
    public Map<String, String> create(String stageName, String className, Map<String, String> values) throws IOException {
        Stage stage;

        try (Engine engine = engine()) {
            try {
                engine.helmRead(stageName);
                throw new FileAlreadyExistsException(stageName);
            } catch (FileNotFoundException e) {
                // OK, fall through
            }
            stage = Stage.create(caller, engine, configuration, stageName, className, values);
            return stage.urlMap();
        }
    }

    @Override
    public void publish(String name, String clazz, Map<String, String> values) throws IOException {
        Stage stage;

        try (Engine engine = engine()) {
            stage = configuration.load(engine, name);
            stage.publish(caller, engine, /* TODO clazz, */ values);
        }
    }

    @Override
    public String version() throws IOException {
        return Main.versionString(World.create() /* TODO */);
    }

    @Override
    public void delete(String stage) throws IOException {
        try (Engine engine = engine()) {
            configuration.load(engine, stage).uninstall(engine);
        }
    }

    @Override
    public Map<String, String> getValues(String stageName) throws IOException {
        Map<String, String> result;
        Stage stage;

        result = new HashMap<>();
        try (Engine engine = engine()) {
            stage = configuration.load(engine, stageName);
            for (Value value : stage.values()) {
                result.put(value.name(), value.disclose());
            }
            return result;
        }
    }

    @Override
    public Map<String, String> setValues(String name, Map<String, String> values) throws IOException {
        Stage stage;
        Value value;
        Map<String, String> clientValues;
        Map<String, String> result;

        try (Engine engine = engine()) {
            stage = configuration.load(engine, name);
            result = new HashMap<>();
            clientValues = new HashMap<>();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                value = stage.value(entry.getKey());
                value = value.withNewValue(entry.getValue());
                clientValues.put(entry.getKey(), value.get());
                result.put(value.name(), value.disclose());
            }
            stage.publish(caller, engine, clientValues);
            return result;
        }
    }

    @Override
    public List<String> validate(String stage, boolean email, boolean repair) throws IOException {
        List<String> output;

        try (Engine engine = engine()) {
            output = new Validation(configuration, configuration.createUserManager() /* TODO */, engine, caller).run(stage, email, repair);
        } catch (MessagingException e) {
            throw new IOException("email failure: " + e.getMessage(), e);
        }
        return output;
    }

    @Override
    public List<String> images(String imageName) throws IOException {
        Registry registry;
        List<TagInfo> all;
        List<String> result;
        String path;

        registry = configuration.createRegistry(World.create() /* TODO */, imageName);
        path = Registry.getRepositoryPath(Registry.toRepository(imageName));
        all = registry.list(path);
        result = new ArrayList<>();
        for (TagInfo image : all) {
            result.add(image.tag);
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

    @Override
    public Map<String, String> awaitAvailable(String name) throws IOException {
        Stage stage;

        try (Engine engine = engine()) {
            stage = configuration.load(engine, name);
            stage.awaitAvailable(engine);
            return stage.urlMap();
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
            pods = configuration.load(engine, stage).runningPods(engine).values();
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

            return new PodConfig(configuration.kubernetes,
                engine.getNamespace(), engine.getServiceAccountToken(saName), pod.name);
        }
    }

    // TODO: where to call?
    private void schedulePodTokenCleanup(String saName, String roleName, String bindingName, int timeout) {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor();
        Runnable cleanup = new Runnable() {
            public void run() {
                try (Engine engine = Engine.createClusterOrLocal(kubernetesContext)) {
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
            stage = configuration.load(engine, stageName);
            tagInfo =  null; //  TODO stage.tagInfo();
        }
        Expressions.checkFaultPermissions(World.createMinimal() /* TODO */, User.authenticatedOrAnonymous().login, new ArrayList<>() /* TODO */);
        return tagInfo;
    }

    @Override
    public List<String> history(String name) throws IOException {
        Stage s;
        List<String> result;

        try (Engine engine = engine()) {
            s = configuration.load(engine, name);
            result = new ArrayList<>(s.history.size());
            for (HistoryEntry entry : s.history) {
                result.add(entry.toString());
            }
        }
        return result;
    }
}
