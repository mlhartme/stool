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
import io.fabric8.kubernetes.api.model.Quantity;
import net.oneandone.stool.kubernetes.Engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Runtime {
    public final Engine engine;
    public final String image;

    public Runtime(Engine engine, String image) {
        this.engine = engine;
        this.image = image;
    }

    public String exec(String script, List<String> args, Map<String, String> env) throws IOException {
        String name;

        name = UUID.randomUUID().toString();
        ContainerBuilder container;

        container = new ContainerBuilder();
        container.withName("noname").withImage(image).withEnv(envVars(env));

        container.withCommand(new String[] { "sleep", "3600"} );
        engine.podCreate(new PodBuilder()
                .withNewMetadata().withName(name).withLabels(null).endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .withHostname(null)
                .addAllToVolumes(new ArrayList<>())
                .addToContainers(container.build())
                .endSpec().build());
        engine.podExec("ls", "-la", "/usr");
        engine.podDelete(name);
    }

    private static EnvVar envVars(Map<String, String> env) {
        List<EnvVar> result;

        result = new ArrayList<>();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            result.add(new EnvVar(entry.getKey(), entry.getValue(), null));
        }
    }
}
