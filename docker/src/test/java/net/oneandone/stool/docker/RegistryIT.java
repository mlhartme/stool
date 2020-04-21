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
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class RegistryIT {
    @Test
    public void localhost() throws IOException {
        final int registryPort = 5000;
        final String registryPrefix = "localhost:" + registryPort;
        String imageName;
        HttpNode root;
        String container;
        Registry registry;
        Map<Integer, String> ports;
        Writer log;
        ImageInfo info;

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
                    registry = Registry.create(root);
                    assertEquals(Arrays.asList(), registry.catalog());
                    docker.imagePush(imageName);
                    assertEquals(Arrays.asList("registrytest"), registry.catalog());
                    assertEquals(Arrays.asList("1"), registry.tags("registrytest"));
                    info = registry.info("registrytest", "1");
                    System.out.println("digest: " + info.id);
                    System.out.println("labels: " + info.labels);

                    registry.delete("registrytest", info.id);
           /* TODO
                    assertEquals(Arrays.asList("registrytest"), registry.catalog());
                    assertEquals(Arrays.asList("registrytest"), registry.tags("registrytest"));
*/
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
    @Test
    public void portus() throws IOException {
        World world;
        String repository;
        HttpNode root;
        Registry registry;
        Properties p;
        List<String> tags;

        world = World.create();
        p = world.guessProjectHome(getClass()).join("test.properties").readProperties();
        root = (HttpNode) world.validNode("https://" + get(p, "portus"));
        repository = get(p, "repository");
        registry = Registry.create(root, "target/contargo.log");
        try {
            registry.tags(repository);
            fail();
        } catch (AuthException e) {
            // ok
            registry = Registry.portus(root, e.realm, e.service, e.scope, get(p, "user"), get(p, "password"));
            tags = registry.tags(repository);
            System.out.println("tags: " + tags);
            System.out.println("v1 tags: " + registry.portusTags("6"));
            System.out.println("info: " + registry.info(repository, tags.get(0)));
        }
    }

    private static String get(Properties p, String key) throws IOException {
        String value;

        value = p.getProperty(key);
        if (value == null) {
            throw new IOException("property not found: " + key);
        }
        return value;
    }
}
