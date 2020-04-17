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
package net.oneandone.stool.docker;

import com.google.gson.JsonObject;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.http.HttpNode;
import net.oneandone.sushi.util.Strings;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RegistryIT {
    @Test
    public void turnaround() throws IOException {
        final int registryPort = 5000;
        final String registryPrefix = "localhost:" + registryPort;
        String imageName;
        HttpNode root;
        String container;
        Registry registry;
        JsonObject manifest;
        String digest;
        Map<Integer, String> ports;
        Writer log;

        try (Daemon docker = Daemon.create("target/registry-wire.log")) {
            ports = new HashMap<>();
            ports.put(registryPort, "" + registryPort);
            log = new StringWriter();

            container = docker.containerCreate("registry", "registry:2", null,null, false, null, null, null,
                    Collections.emptyMap(), Strings.toMap("REGISTRY_STORAGE_DELETE_ENABLED", "true"), Collections.emptyMap(), ports);
            docker.containerStart(container);
            try {
                imageName = registryPrefix + "/registrytest:1";
                docker.imageBuild(imageName, Collections.emptyMap(),
                        Strings.toMap("label1", "value1", "xyz", "123"),
                        DaemonIT.dockerfile("FROM debian:stretch-slim\nCMD [\"echo\", \"hi\", \"/\"]\n"),
                        false, log);
                try {
                    root = (HttpNode) World.create().validNode("http://" + registryPrefix);
                    registry = new Registry(root);
                    assertEquals(Arrays.asList(), registry.catalog());
                    docker.imagePush(imageName);
                    assertEquals(Arrays.asList("registrytest"), registry.catalog());
                    assertEquals(Arrays.asList("1"), registry.tags("registrytest"));
                    manifest = registry.manifest("registrytest", "1");
                    digest = manifest.get("config").getAsJsonObject().get("digest").getAsString();
                    System.out.println("digest: " + digest);
                    System.out.println("manifest: " + manifest);

                    assertEquals(Strings.toMap("label1", "value1", "xyz", "123"), registry.labels("registrytest", digest));

                    registry.delete("registrytest", digest); // TODO: yields 405 error
                    // TODO: assertEquals(Arrays.asList(), registry.catalog());

                    System.out.println("ok");
                } finally {
                    docker.imageRemove(imageName, false);
                }
            } finally {
                System.out.println("wipe container " + container);
                docker.containerStop(container, 5);
                docker.containerRemove(container);
            }
        }

    }
}
