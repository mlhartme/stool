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
package net.oneandone.stool.registry;

import net.oneandone.stool.docker.Daemon;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
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

public class RegistryIT {
    private static final World WORLD;

    static {
        try {
            WORLD = World.create();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

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
        TagInfo info;

        try (Daemon docker = Daemon.create(/* "target/registry-wire.log" */ null)) {
            ports = new HashMap<>();
            ports.put(registryPort, "" + registryPort);
            log = new StringWriter();

            docker.imagePull("registry:2");
            container = docker.containerCreate("registry", "registry:2", null,null, false, null, null, null,
                    Collections.emptyMap(), Strings.toMap("REGISTRY_STORAGE_DELETE_ENABLED", "true"), Collections.emptyMap(), ports);
            docker.containerStart(container);
            try {
                imageName = registryPrefix + "/registrytest:1";
                docker.imageBuild(imageName, Collections.emptyMap(),
                        Strings.toMap("label1", "value1", "xyz", "123"),
                        dockerfile("FROM debian:stretch-slim\nCMD [\"echo\", \"hi\", \"/\"]\n"),
                        false, log);
                try {
                    root = (HttpNode) WORLD.validNode("http://" + registryPrefix);
                    registry = Registry.local(root);
                    assertEquals(Arrays.asList(), registry.catalog());
                    docker.imagePush(imageName);
                    assertEquals(Arrays.asList("registrytest"), registry.catalog());
                    assertEquals(Arrays.asList("1"), registry.tags("registrytest"));
                    info = registry.info("registrytest", "1");
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
        String repository;
        Registry registry;
        Properties p;
        List<String> tags;

        p = WORLD.guessProjectHome(getClass()).join("test.properties").readProperties();

        repository = get(p, "repository");
        registry = Registry.portus(WORLD, get(p, "portus"), "target/portus-wire.log");
        tags = registry.tags(repository);
        System.out.println("tags: " + tags);
        System.out.println("info: " + registry.info(repository, tags.get(0)));
        System.out.println("v1 tags: " + registry.portusTags("6"));
    }

    @Test
    public void portusDelete() throws IOException {
        String repository;
        Registry registry;
        Properties p;
        List<String> tags;

        p = WORLD.guessProjectHome(getClass()).join("test.properties").readProperties();

        repository = "cisoops-public/waterloo/app.foo";
        registry = Registry.portus(WORLD, get(p, "portus"), "target/portus-wire.log");
        tags = registry.tags(repository);
        System.out.println("tags: " + tags);
        registry.portusDelete(repository);
        tags = registry.tags(repository);
        System.out.println("tags: " + tags);
    }

    private static String get(Properties p, String key) throws IOException {
        String value;

        value = p.getProperty(key);
        if (value == null) {
            throw new IOException("property not found: " + key);
        }
        return value;
    }

    public static FileNode dockerfile(String dockerfile, FileNode ... extras) throws IOException {
        FileNode dir;

        dir = WORLD.getTemp().createTempDirectory();
        dir.join("Dockerfile").writeString(dockerfile);
        for (FileNode extra : extras) {
            extra.copyFile(dir.join(extra.getName()));
        }
        return dir;
    }
}
