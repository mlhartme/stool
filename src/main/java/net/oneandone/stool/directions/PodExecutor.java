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

public class PodExecutor extends Executor {
    public final Engine engine;
    public final String image;
    public final FileNode working;

    public PodExecutor(Engine engine, String image, FileNode working) {
        this.engine = engine;
        this.image = image;
        this.working = working;
    }

    public String exec(Script script, List<String> args, Map<String, String> env) throws IOException {
        final String scriptsPath = "/usr/local/toolkit/scripts";
        final String workingPath = "/usr/local/working";
        String pod;
        ContainerBuilder cb;
        String container;
        String result;

        pod = UUID.randomUUID().toString();
        container = "toolkit";
        cb = new ContainerBuilder();
        cb.withName(container)
                .withImage(image)
                .withWorkingDir(scriptsPath)
                .withEnv(envVars(env))
                .withImagePullPolicy("Never")
                .withCommand("sleep", "3600");
        engine.podCreate(new PodBuilder()
                .withNewMetadata().withName(pod).endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .withTerminationGracePeriodSeconds((long) 0)
                .addAllToVolumes(new ArrayList<>())
                .addToContainers(cb.build())
                .endSpec().build());
        try {
            engine.podUpload(pod, container, working, workingPath);
            result = engine.podExec(pod, "toolkit",
                    Strings.cons(scriptsPath + "/" + script.name + ".sh",
                            Strings.toArray(replacePrefix(args, working.getAbsolute() + "/", workingPath + "/"))));
            engine.podDownload(pod, container, workingPath, working.deleteTree());
            return result;
        } finally {
            engine.podDelete(pod);
        }
    }

    // TODO: doesn't work for working path itself
    private static List<String> replacePrefix(List<String> lst, String inPrefix, String outPrefix) {
        List<String> result;

        result = new ArrayList<>();
        for (String item : lst) {
            if (item.startsWith(inPrefix)) {
                result.add(outPrefix + item.substring(inPrefix.length()));
            } else {
                result.add(item);
            }
        }
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
}
