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
import net.oneandone.stool.util.Secrets;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.HttpNode;
import net.oneandone.sushi.util.Strings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RegistryIT {
    private static final World WORLD;

    static {
        try {
            WORLD = World.create();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test // TODO
    public void docker() throws IOException {
        HttpNode root;
        String container;
        Registry registry;
        Map<Integer, String> ports;

        try (Daemon docker = Daemon.create(/* "target/registry-wire.log" */ null)) {
            final int registryPort = 5000;
            final String registryPrefix = docker.getHost() + ":" + registryPort;

            ports = new HashMap<>();
            ports.put(registryPort, "" + registryPort);
            docker.imagePull("registry:2");
            container = docker.containerCreate("registry", "registry:2", null,null, false, null, null, null,
                    Collections.emptyMap(), Strings.toMap("REGISTRY_STORAGE_DELETE_ENABLED", "true"), Collections.emptyMap(), ports);
            docker.containerStart(container);
            try {
                root = (HttpNode) WORLD.validNode("http://" + registryPrefix);
                registry = DockerRegistry.create(root, "target/docker-registry.log");
                run(docker, registry, registryPrefix, "registrytest", false /* TODO */);
            } finally {
                docker.containerStop(container, 5);
                docker.containerRemove(container);
            }
        }
    }

    @Test
    public void portus() throws IOException {
        URI registryUri;
        Registry registry;
        String registryPrefix;
        String repository;

        registryUri = Secrets.load(WORLD).portus.resolve("it-todo"); // TODO: include hostname in prefix
        registryPrefix = registryUri.getHost() + registryUri.getPath();
        repository = registryPrefix.substring(registryPrefix.indexOf('/') + 1) + "/registrytest";
        registry = PortusRegistry.create(WORLD, registryUri.toString(), "target/portus-wire.log");
        try {
            registry.delete(repository);
        } catch (PortusRegistry.RepositoryNotFoundException e) {
            // ok
        }
        try (Daemon docker = Daemon.create(/* "target/registry-wire.log" */ null)) {
            run(docker, registry, registryPrefix, repository, true);
        }
    }

    @Test // TODO
    public void portusChart() throws IOException {
        URI registryUri;
        PortusRegistry registry;
        String repository;

        registryUri = Secrets.load(WORLD).portus.resolve("/");
        repository = "cisoops-public/charts/kutter";
        registry = PortusRegistry.create(WORLD, registryUri.toString(), "target/portus-wire.log");
        System.out.println("uri: " + registryUri);
        System.out.println("repo: " + repository);
        System.out.println("tags: " + registry.tags(repository));
        System.out.println("dockerTags: " + registry.helmTags(repository));
    }

    private void run(Daemon docker, Registry registry, String registryPrefix, String repository, boolean testDelete) throws IOException {
        String imageName;
        Writer log;

        log = new StringWriter();
        imageName = registryPrefix + "/registrytest:1";
        docker.imageBuild(imageName, Collections.emptyMap(),
                Strings.toMap("label1", "value1", "xyz", "123"),
                dockerfile("FROM debian:stretch-slim\nCMD [\"echo\", \"hi\", \"/\"]\n"),
                false, log);
        try {
            assertEquals(Arrays.asList(), registry.tags(repository));
            System.out.println("image push: " + docker.imagePush(imageName));
            try { // TODO
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            assertTrue(registry.list().contains(repository));
            assertEquals(Arrays.asList("1"), registry.tags(repository));
            try {
                assertEquals("1", registry.info(repository, "1").tag);
            } catch (NumberFormatException e) {
                e.printStackTrace();
                e.getCause().printStackTrace();
            }
            registry.delete(repository);
            if (testDelete) {
                assertEquals(Arrays.asList(), registry.tags(repository));
                assertFalse(registry.list().contains(repository));
            }
        } finally {
            docker.imageRemove(imageName, false);
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
