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
package net.oneandone.stool.directions;

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PodBuilder;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** execute script in a pod - used when toolkit is configued by an image */
public class PodExecutor extends Executor {
    private static final String CONTAINER = "toolkit";
    private static final String SCRIPTS_PATH = "/usr/local/toolkit/scripts";
    private static final String WORKING_PATH = "/usr/local/working";

    public final Engine engine;
    public final String image;

    private String pod;

    public PodExecutor(Engine engine, String image, Map<String, String> environment, FileNode working) {
        super(environment, working);
        this.engine = engine;
        this.image = image;
        this.pod = null;
    }

    public void lazyStart() throws IOException {
        ContainerBuilder cb;

        if (pod != null) {
            return;
        }
        pod = UUID.randomUUID().toString();
        cb = new ContainerBuilder();
        cb.withName(CONTAINER)
                .withImage(image)
                .withWorkingDir(SCRIPTS_PATH)
                .withEnv(envVars(environment))
                .withCommand("sleep", "3600");
        engine.podCreate(new PodBuilder()
                .withNewMetadata().withName(pod).endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .withTerminationGracePeriodSeconds((long) 0)
                .addAllToVolumes(new ArrayList<>())
                .addToContainers(cb.build())
                .endSpec().build());
        engine.podUpload(pod, CONTAINER, storageRoot, WORKING_PATH);
    }

    public String exec(Script script, List<String> args) throws IOException {
        String result;
        String persitentPath;
        List<String> cmd;

        lazyStart();
        engine.podUpload(pod, CONTAINER, storageRoot, WORKING_PATH);
        persitentPath = WORKING_PATH + "/" + script.name;
        cmd = new ArrayList<>();
        cmd.add(SCRIPTS_PATH + "/" + script.name + ".sh");
        for (String arg : args) {
            if (Freemarker.STORAGE.equals(arg)) {
                result = engine.podExec(pod, "toolkit", "mkdir", "-p", persitentPath);
                if (!result.isEmpty()) {
                    throw new IOException("mkdir output: " + result);
                }
                cmd.add(WORKING_PATH + "/" + script.name);
            } else {
                cmd.add(arg);
            }
        }
        result = engine.podExec(pod, "toolkit", Strings.toArray(cmd));
        return result;
    }

    private static List<EnvVar> envVars(Map<String, String> env) {
        List<EnvVar> result;

        result = new ArrayList<>();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            result.add(new EnvVar(entry.getKey(), entry.getValue(), null));
        }
        return result;
    }

    public void close() throws IOException {
        if (pod != null) {
            engine.podDelete(pod);
            pod = null;
        }
    }
}
