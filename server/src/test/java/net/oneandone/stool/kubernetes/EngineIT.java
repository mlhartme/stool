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
package net.oneandone.stool.kubernetes;

import net.oneandone.stool.docker.ContainerInfo;
import net.oneandone.stool.docker.Daemon;
import net.oneandone.stool.docker.Stats;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotEquals;

public class EngineIT {
    private static final World WORLD = World.createMinimal();

    private Engine create() throws IOException {
        return Engine.local();
    }

    @BeforeClass
    public static void beforeClass() throws IOException {
        Engine engine;

        engine = Engine.local();
        engine.namespaceReset();
    }

    private FileNode dockerfile(String dockerfile, FileNode ... extras) throws IOException {
        FileNode dir;

        dir = WORLD.getTemp().createTempDirectory();
        dir.join("Dockerfile").writeString(dockerfile);
        for (FileNode extra : extras) {
            extra.copyFile(dir.join(extra.getName()));
        }
        return dir;
    }

    //-- pods

    @Test
    public void podTerminating() throws IOException {
        final String imageTag = "foobla";
        final String name = "pod";
        String image;
        Collection<PodInfo> lst;
        PodInfo info;

        try (Engine engine = create(); Daemon docker = Daemon.create()) {
            assertEquals(Collections.emptyMap(), engine.podList());
            image = docker.imageBuild(imageTag, Collections.emptyMap(), Collections.emptyMap(),
                    dockerfile("FROM debian:stretch-slim\nCMD echo ho\n"), false, null);
            assertFalse(engine.podCreate(name, imageTag, "foo", "bar"));
            assertEquals(Daemon.Status.EXITED, engine.podContainerStatus(name));
            lst = engine.podList().values();
            assertEquals(1, lst.size());
            info = lst.iterator().next();
            assertEquals(name, info.name);
            assertEquals("Succeeded", info.phase);
            assertEquals(Strings.toMap("foo", "bar"), info.labels);
            assertEquals(Daemon.Status.EXITED, engine.podContainerStatus(name));
            podDelete(engine, docker, name);
            assertEquals(Collections.emptyMap(), docker.containerListForImage(image));
            assertEquals(0, engine.podList().size());
            docker.imageRemove(imageTag, false);
        }
    }

    @Test
    public void podHealing() throws IOException, InterruptedException {
        Map<String, String> labels;
        List<String> ids;
        String image;
        String pod = "mhm";
        String container;
        String containerHealed;
        Map<String, ContainerInfo> map;
        Stats stats;

        labels = Strings.toMap("stooltest", UUID.randomUUID().toString());
        try (Engine engine = create(); Daemon docker = Daemon.create()) {
            assertTrue(docker.imageList(labels).isEmpty());
            docker.imageBuild("some:tag", Collections.emptyMap(), labels, dockerfile("FROM debian:stretch-slim\nRUN touch abc\nCMD sleep 5\n"), false, null);
            ids = new ArrayList<>(docker.imageList(labels).keySet());
            assertEquals(1, ids.size());
            image = ids.get(0);
            assertTrue(docker.containerListForImage(image).isEmpty());
            assertTrue(docker.containerList("stooltest").isEmpty());
            engine.podCreate(pod, "some:tag", null,true, null, Strings.toMap("containerLabel", "bla"),
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList());
            assertEquals(Daemon.Status.RUNNING, engine.podContainerStatus(pod));

            container = engine.podProbe(pod).containerId;
            stats = docker.containerStats(container);
            assertEquals(0, stats.cpu);

            map = docker.containerListForImage(image);
            assertEquals(1, map.size());
            assertTrue(map.containsKey(container));

            assertEquals(Arrays.asList(container), new ArrayList<>(docker.containerList("stooltest").keySet()));
            map = docker.containerListForImage(image);
            assertEquals(1, map.size());
            assertTrue(map.containsKey(container));
            assertEquals(Daemon.Status.RUNNING, map.get(container).state);

            docker.containerStop(container, 5);
            Thread.sleep(2500);

            map = docker.containerListForImage(image);
            containerHealed = map.keySet().iterator().next();
            assertNotEquals(container, containerHealed);
            assertEquals(Daemon.Status.RUNNING, engine.podContainerStatus(pod));

            assertEquals(Arrays.asList(containerHealed), new ArrayList<>(docker.containerListForImage(image).keySet()));

            podDelete(engine, docker, pod);

            assertTrue(docker.containerListForImage(image).isEmpty());
            docker.imageRemove(image, false);
            assertEquals(new HashMap<>(), docker.imageList(labels));
        }
    }


    @Test
    public void podRestart() throws IOException {
        String image;
        String message;

        message = UUID.randomUUID().toString();
        try (Engine engine = create(); Daemon docker = Daemon.create()) {
            image = docker.imageBuild("restart:tag", Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD echo " + message + "; sleep 3\n"), false, null);
            assertTrue(engine.podCreate("restart-pod", "restart:tag"));
        }
        try (Engine engine = Engine.local(); Daemon docker = Daemon.create()) {
            podDelete(engine, docker,"restart-pod");
            docker.imageRemove(image, false);
        }
        try (Engine engine = Engine.local(); Daemon docker = Daemon.create()) {
            image = docker.imageBuild("restart:tag", Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD echo " + message + "; sleep 3\n"), false, null);
            assertTrue(engine.podCreate("restart-pod", "restart:tag"));
        }
        try (Engine engine = Engine.local(); Daemon docker = Daemon.create()) {
            podDelete(engine, docker,"restart-pod");
            docker.imageRemove(image, false);
        }
    }

    @Test
    public void podEnv() throws IOException {
        String image = "stooltest";
        String pod = "podenv";
        String output;

        try (Engine engine = create(); Daemon docker = Daemon.create()) {
            output = imageBuildWithOutput(docker, image, dockerfile("FROM debian:stretch-slim\nCMD echo $foo $notfound $xxx\n"));
            assertNotNull(output);
            assertFalse(engine.podCreate(pod, image, Strings.toMap(), Strings.toMap("foo", "bar", "xxx", "after")));
            output = engine.podLogs(pod);
            assertEquals("bar after\n", output);
            podDelete(engine, docker, pod);
            docker.imageRemove(image, false);
        }
    }

    @Test
    public void podImplicitHostname() throws IOException, InterruptedException {
        doHostnameTest("podimplicit", null, "podimplicit");
    }

    @Test
    public void podExplicitHostname() throws IOException, InterruptedException {
        doHostnameTest("podexplicit", "ex", "ex");
    }

    private void doHostnameTest(String pod, String hostname, String expected) throws IOException, InterruptedException {
        String image = "hostname";
        String output;

        try (Engine engine = create(); Daemon docker = Daemon.create()) {
            output = imageBuildWithOutput(docker, image, dockerfile("FROM debian:stretch-slim\nRUN echo pod\nCMD hostname\n"));
            assertNotNull(output);
            assertFalse(engine.podCreate(pod, image, hostname, false, null, Strings.toMap(), Strings.toMap(),
                    Collections.emptyMap(), Collections.emptyList()));
            assertEquals(Daemon.Status.EXITED, engine.podContainerStatus(pod));
            assertEquals(expected + "\n", engine.podLogs(pod));
            podDelete(engine, docker, pod);
            docker.imageRemove(image, false);
        }
    }

    @Test
    public void podMount() throws IOException {
        FileNode home;
        FileNode file;
        String image = "stooltest";
        String pod = "bindmount";
        String output;

        home = WORLD.getHome();
        file = home.createTempFile();
        try (Engine engine = create(); Daemon docker = Daemon.create()) {
            output = imageBuildWithOutput(docker, image, dockerfile("FROM debian:stretch-slim\nCMD ls " + file.getAbsolute() + "\n"));
            assertNotNull(output);

            assertFalse(engine.podCreate(pod, image, null,false, null, Collections.emptyMap(), Collections.emptyMap(),
                    Collections.singletonMap(home, home.getAbsolute()), Collections.emptyList()));
            output = engine.podLogs(pod);
            assertTrue(output.contains(file.getAbsolute()));
            podDelete(engine, docker, pod);

            docker.imageRemove(image, true);
        }
    }

    @Test
    public void podLimit() throws IOException {
        final int limit = 1024*1024*5;
        Stats stats;
        String image = "podlimit";
        String pod = "pod";
        String message;
        String container;

        message = UUID.randomUUID().toString();
        try (Engine engine = create(); Daemon docker = Daemon.create()) {
            docker.imageBuild(image, Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD echo " + message + "; sleep 3\n"), false, null);
            engine.podCreate(pod, image, null,false, limit, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyList());
            container = engine.podProbe(pod).containerId;
            stats = docker.containerStats(container);
            assertEquals(limit, stats.memoryLimit);
            assertTrue(stats.memoryUsage <= stats.memoryLimit);
            podDelete(engine, docker, pod);
            docker.imageRemove(image, false);
        }
    }

    //-- services

    @Test
    public void services() throws IOException {
        final String name = "service";
        ServiceInfo info;

        try (Engine engine = create()) {
            assertEquals(0, engine.serviceList().size());
            engine.serviceCreate(name, 30001, 8080);
            info = engine.serviceList().get(name);
            assertEquals(30001, info.nodePort);
            assertEquals(8080, info.containerPort);
            engine.serviceDelete(name);
            assertEquals(0, engine.serviceList().size());
        }
    }

    //-- misc

    @Test
    public void secrets() throws IOException {
        final String name = "sec";
        Data data;

        try (Engine engine = create(); Daemon docker = Daemon.create()) {
            data = Data.secrets(name, "/etc/secrets");
            data.addUtf8("sub/renamed.txt", "blablub");
            data.define(engine);

            assertTrue(engine.secretList().containsKey(name));
            docker.imageBuild("secuser", Collections.emptyMap(), Collections.emptyMap(),
                    dockerfile("FROM debian:stretch-slim\nCMD cat /etc/secrets/sub/renamed.txt\n"), false, null);

            assertFalse(engine.podCreate(name, "secuser", "somehost", false, null,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.singletonList(data)));
            assertEquals("blablub", engine.podLogs(name));
            podDelete(engine, docker, name);
            engine.secretDelete(name);
        }
    }

    @Test
    public void configMap() throws IOException {
        final String name = "cm";
        Data data;

        try (Engine engine = create(); Daemon docker = Daemon.create()) {
            data = Data.configMap(name, "/etc", true);
            data.addUtf8("test.yaml", "123");
            data.addUtf8("sub/file", "foo");
            data.define(engine);

            assertTrue(engine.configMapList().containsKey(name));

            docker.imageBuild("config", Collections.emptyMap(), Collections.emptyMap(),
                    dockerfile("FROM debian:stretch-slim\nCMD cat /etc/test.yaml /etc/sub/file\n"), false, null);

            assertFalse(engine.podCreate(name, "config", "somehost", false, null,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.singletonList(data)));
            assertEquals("123foo", engine.podLogs(name));

            podDelete(engine, docker, name);
            engine.configMapDelete(name);;
        }
    }

    //--

    private String imageBuildWithOutput(Daemon docker, String repositoryTag, FileNode context) throws IOException {
        try (StringWriter dest = new StringWriter()) {
            docker.imageBuild(repositoryTag, Collections.emptyMap(), Collections.emptyMap(), context, false, dest);
            return dest.toString();
        }
    }

    // TODO
    public static void podDelete(Engine engine, Daemon docker, String podName) throws IOException {
        String container;

        container = engine.podDelete(podName);
        try {
            docker.containerRemove(container);
        } catch (net.oneandone.sushi.fs.FileNotFoundException e) {
            // fall-through, already deleted
        }
        // TODO: what if there's more than one container for this pod?
    }

}
