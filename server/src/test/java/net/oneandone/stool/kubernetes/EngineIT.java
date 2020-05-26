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
        return Engine.create();
    }

    @BeforeClass
    public static void beforeClass() throws IOException {
        try {
            Engine engine;

            engine = Engine.create();
            engine.namespaceReset();
        } catch (Exception e) {
            // TODO: junit silently ignores exceptions here ...
            e.printStackTrace();
            throw e;
        }
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
        final String name = "pod";
        Collection<PodInfo> lst;
        PodInfo info;

        try (Engine engine = create()) {
            assertEquals(Collections.emptyMap(), engine.podList());
            assertFalse(engine.podCreate(name, "debian:stretch-slim", false, new String[] { "sh", "-c", "echo ho" }, "foo", "bar"));
            assertEquals(Daemon.Status.EXITED, engine.podContainerStatus(name));
            assertEquals("ho\n", engine.podLogs(name));
            lst = engine.podList().values();
            assertEquals(1, lst.size());
            info = lst.iterator().next();
            assertEquals(name, info.name);
            assertEquals("Succeeded", info.phase);
            assertEquals(Strings.toMap("foo", "bar"), info.labels);
            assertEquals(Daemon.Status.EXITED, engine.podContainerStatus(name));
            engine.podDelete(name);
            assertEquals(0, engine.podList().size());
        }
    }

    @Test
    public void podHealing() throws IOException, InterruptedException {
        String image;
        String pod = "mhm";
        String container;
        String containerHealed;
        Map<String, ContainerInfo> map;
        Stats stats;

        try (Engine engine = create(); Daemon docker = Daemon.create()) {
            image = "debian:stretch-slim";
            assertTrue(docker.containerListForImage(image).isEmpty());
            assertTrue(docker.containerList("stooltest").isEmpty());
            engine.podCreate(pod, image, false, new String[] { "sleep", "5"},null,true, null, Strings.toMap("containerLabel", "bla"),
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList());
            assertEquals(Daemon.Status.RUNNING, engine.podContainerStatus(pod));

            container = engine.podProbe(pod).containerId;
            stats = docker.containerStats(container);
            assertEquals(0, stats.cpu);

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
        }
    }


    @Test
    public void podRestart() throws IOException {
        try (Engine engine = create()) {
            assertTrue(engine.podCreate("restart-pod", "debian:stretch-slim", false, new String[] { "sleep", "3"}));
        }
        try (Engine engine = Engine.create()) {
            engine.podDelete("restart-pod");
        }
        try (Engine engine = Engine.create()) {
            assertTrue(engine.podCreate("restart-pod", "debian:stretch-slim", false, new String[] { "sleep", "3"}));
        }
        try (Engine engine = Engine.create()) {
            engine.podDelete("restart-pod");
        }
    }

    @Test
    public void podEnv() throws IOException {
        String pod = "podenv";
        String output;

        try (Engine engine = create()) {
            assertFalse(engine.podCreate(pod, "debian:stretch-slim", false, new String[] { "sh", "-c", "echo $foo $notfound $xxx"},
                    Strings.toMap(), Strings.toMap("foo", "bar", "xxx", "after")));
            output = engine.podLogs(pod);
            assertEquals("bar after\n", output);
            engine.podDelete(pod);
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
        try (Engine engine = create()) {
            assertFalse(engine.podCreate(pod, "debian:stretch-slim", false,
                    new String[] { "hostname"}, hostname, false, null, Strings.toMap(), Strings.toMap(),
                    Collections.emptyMap(), Collections.emptyList()));
            assertEquals(Daemon.Status.EXITED, engine.podContainerStatus(pod));
            assertEquals(expected + "\n", engine.podLogs(pod));
            engine.podDelete(pod);
        }
    }

    @Test
    public void podMount() throws IOException {
        FileNode home;
        FileNode file;
        String pod = "bindmount";
        String output;

        home = WORLD.getHome();
        file = home.createTempFile();
        try (Engine engine = create()) {
            assertFalse(engine.podCreate(pod, "debian:stretch-slim", false, new String[] { "ls", file.getAbsolute()},
                    null,false, null, Collections.emptyMap(), Collections.emptyMap(),
                    Collections.singletonMap(home, home.getAbsolute()), Collections.emptyList()));
            output = engine.podLogs(pod);
            assertTrue(output, output.contains(file.getAbsolute()));
            engine.podDelete(pod);
        }
    }

    @Test
    public void podLimit() throws IOException {
        final int limit = 1024*1024*5;
        Stats stats;
        String pod = "pod";
        String container;

        try (Engine engine = create(); Daemon docker = Daemon.create()) {
            engine.podCreate(pod, "debian:stretch-slim", false, new String[] { "sleep", "3" },
                    null,false, limit, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyList());
            container = engine.podProbe(pod).containerId;
            stats = docker.containerStats(container);
            assertEquals(limit, stats.memoryLimit);
            assertTrue(stats.memoryUsage <= stats.memoryLimit);
            engine.podDelete(pod);
        }
    }

    //-- services

    @Test
    public void services() throws IOException {
        final String name = "service";
        ServiceInfo info;

        try (Engine engine = create()) {
            assertEquals(0, engine.serviceList().size());
            engine.serviceCreate(name, 1234, 8080);
            info = engine.serviceList().get(name);
            assertEquals(1234, info.port);
            engine.serviceDelete(name);
            assertEquals(0, engine.serviceList().size());
        }
    }

    //-- ingress

    @Test
    public void ingress() throws IOException {
        try (Engine engine = create()) {
            engine.ingressCreate("ingress", "some-service", "some-host", 1234);
            engine.ingressDelete("ingress");
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

            assertFalse(engine.podCreate(name, "secuser", false, null,"somehost", false, null,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.singletonList(data)));
            assertEquals("blablub", engine.podLogs(name));
            podDelete(engine, docker, name);
            engine.secretDelete(name);
        }
    }

    @Test
    public void configMap() throws IOException {
        Map<String, String> data;
        final String name = "cm";

        data = Strings.toMap("test.yaml", "123", "sub-file", "foo");
        try (Engine engine = create(); Daemon docker = Daemon.create()) {
            engine.configMapCreate(name, data);
            assertEquals(data, engine.configMapRead(name));
            engine.configMapDelete(name);;
        }
    }

    @Test
    public void configMapBinary() throws IOException {
        final String name = "cmbinary";
        Data data;

        try (Engine engine = create(); Daemon docker = Daemon.create()) {
            data = Data.configMap(name, "/etc", true);
            data.addUtf8("test.yaml", "123");
            data.addUtf8("sub/file", "foo");
            data.define(engine);

            assertTrue(engine.configMapList().containsKey(name));

            docker.imageBuild("config", Collections.emptyMap(), Collections.emptyMap(),
                    dockerfile("FROM debian:stretch-slim\nCMD cat /etc/test.yaml /etc/sub/file\n"), false, null);

            assertFalse(engine.podCreate(name, "config", false, null,"somehost", false, null,
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
