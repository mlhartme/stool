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
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.directions.Directions;
import net.oneandone.stool.core.LocalSettings;
import net.oneandone.stool.core.Field;
import net.oneandone.stool.directions.DirectionsRef;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.kubernetes.PodInfo;
import net.oneandone.stool.registry.Registry;
import net.oneandone.stool.registry.TagInfo;
import net.oneandone.stool.Main;
import net.oneandone.stool.core.Stage;
import net.oneandone.stool.core.HistoryEntry;
import net.oneandone.stool.util.Diff;
import net.oneandone.stool.util.Pair;
import net.oneandone.stool.core.PredicateParser;
import net.oneandone.stool.core.Validation;
import net.oneandone.stool.directions.Variable;

import javax.mail.MessagingException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class KubernetesClient extends Client {
    private final ObjectMapper json;

    private final LocalSettings localSettings;

    /** null for cluster */
    private final String kubernetesContext;

    public KubernetesClient(LocalSettings localSettings, String context, String kubernetesContext, Caller caller) {
        super(context, caller);
        this.json = localSettings.json;
        this.localSettings = localSettings;
        this.kubernetesContext = kubernetesContext;
    }

    public Engine engine() {
        return Engine.createClusterOrLocal(localSettings.json, kubernetesContext);
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
            for (Stage stage : localSettings.list(engine, new PredicateParser(engine).parse(filter), problems)) {
                s = new HashMap<>();
                result.put(stage.getName(), s);
                remaining = new ArrayList<>(select);
                for (Field property : stage.fields()) {
                    if ((select.isEmpty() && (hidden || !property.hidden)) || remaining.remove(property.name())) {
                        s.put(property.name(), property.getAsJson(json, engine));
                    }
                }
                // add values explicitly selected
                for (Variable variable : stage.variables()) {
                    if (!select.isEmpty() && remaining.remove(variable.property.name)) {
                        if (!variable.property.privt) {
                            s.put(variable.property.name, new TextNode(variable.get()));
                        }
                    }
                }
                if (!remaining.isEmpty()) {
                    throw new IOException("select argument: unknown property/field(s): " + remaining);
                }
            }
            if (!problems.isEmpty()) {
                throw new IOException("nested problems: " + problems);
            }
            return result;
        }
    }

    @Override
    public Map<String, String> create(String stageName, DirectionsRef directionsRef, Map<String, String> values) throws IOException {
        Stage stage;

        try (Engine engine = engine()) {
            try {
                engine.helmRead(stageName);
                throw new FileAlreadyExistsException(stageName);
            } catch (FileNotFoundException e) {
                // OK, fall through
            }
            stage = Stage.create(caller, kubernetesContext, engine, localSettings, stageName, directionsRef, values);
            return stage.urlMap();
        }
    }

    @Override
    public Diff publish(String name, boolean dryrun, String allow, DirectionsRef directionsRefOpt, Map<String, String> values) throws IOException {
        Stage stage;
        Directions directions;

        try (Engine engine = engine()) {
            stage = localSettings.load(engine, name);
            directions = directionsRefOpt == null ? stage.directions : directionsRefOpt.resolve(localSettings);
            return stage.publish(caller, kubernetesContext, engine, dryrun, allow, directions, values);
        }
    }

    @Override
    public String version() throws IOException {
        return Main.versionString(localSettings.world);
    }

    @Override
    public void delete(String stage) throws IOException {
        try (Engine engine = engine()) {
            localSettings.load(engine, stage).uninstall(kubernetesContext, engine);
        }
    }

    @Override
    public Map<String, Pair> getValues(String stageName) throws IOException {
        Map<String, Pair> result;
        Stage stage;

        result = new LinkedHashMap<>();
        try (Engine engine = engine()) {
            stage = localSettings.load(engine, stageName);
            for (Variable variable : stage.variables()) {
                if (!variable.property.privt) {
                    result.put(variable.property.name, new Pair(variable.get(), variable.property.doc));
                }
            }
            return result;
        }
    }

    @Override
    public Map<String, String> setValues(String name, Map<String, String> values) throws IOException {
        Stage stage;
        Variable variable;
        Map<String, String> changes;
        Map<String, String> result;

        try (Engine engine = engine()) {
            stage = localSettings.load(engine, name);
            result = new LinkedHashMap<>();
            changes = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                variable = stage.variable(entry.getKey());
                if (variable.property.privt) {
                    throw new ArgumentException("cannot set private value: " + variable.property.name);
                }
                variable = variable.withNewValue(entry.getValue());
                changes.put(entry.getKey(), variable.get());
                result.put(variable.property.name, variable.get());
            }
            stage.setValues(caller, kubernetesContext, engine, changes);
            return result;
        }
    }

    @Override
    public List<String> validate(String stage, boolean email, boolean repair) throws IOException {
        List<String> output;

        try (Engine engine = engine()) {
            output = new Validation(kubernetesContext, localSettings, localSettings.createUserManager() /* TODO */, engine, caller).run(stage, email, repair);
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

        registry = localSettings.createRegistry(imageName);
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
            stage = localSettings.load(engine, name);
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
        try (Engine engine = engine()) {
            pods = localSettings.load(engine, stage).runningPods(engine).values();
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

            return new PodConfig(localSettings.kubernetes, engine.getNamespace(), engine.getServiceAccountToken(saName), pod.name);
        }
    }

    // TODO: where to call?
    private void schedulePodTokenCleanup(String saName, String roleName, String bindingName, int timeout) {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor();
        Runnable cleanup = new Runnable() {
            public void run() {
                try (Engine engine = Engine.createClusterOrLocal(localSettings.json, kubernetesContext)) {
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

    @Override
    public List<String> history(String name) throws IOException {
        Stage s;
        List<String> result;

        try (Engine engine = engine()) {
            s = localSettings.load(engine, name);
            result = new ArrayList<>(s.history.size());
            for (HistoryEntry entry : s.history) {
                result.add(entry.toString());
            }
        }
        return result;
    }
}
