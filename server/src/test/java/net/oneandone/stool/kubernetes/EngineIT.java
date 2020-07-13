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

import net.oneandone.stool.docker.Daemon;
import net.oneandone.stool.docker.Stats;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotEquals;

public class EngineIT {
    private static final World WORLD = World.createMinimal();

    private static Engine create() throws IOException {
        return Engine.create(WORLD, "stool-engine-it"); // TODO
    }

    @BeforeClass
    public static void beforeClass() throws IOException {
        Engine engine;

        engine = create();
        engine.namespaceReset();
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
        String containerOrig;
        String containerHealed;

        try (Engine engine = create(); Daemon docker = Daemon.create()) {
            image = "debian:stretch-slim";
            engine.podCreate(pod, image, false, new String[] { "sleep", "5"},null,true, null, Strings.toMap("containerLabel", "bla"),
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList());
            assertEquals(Daemon.Status.RUNNING, engine.podContainerStatus(pod));

            containerOrig = engine.podProbe(pod).containerId;
            docker.containerStop(containerOrig, 5);
            Thread.sleep(2500);

            assertEquals(Daemon.Status.RUNNING, engine.podContainerStatus(pod));
            containerHealed = engine.podProbe(pod).containerId;
            assertNotEquals(containerOrig, containerHealed);

            engine.podDelete(pod);
        }
    }


    @Test
    public void podRestart() throws IOException {
        try (Engine engine = create()) {
            assertTrue(engine.podCreate("restart-pod", "debian:stretch-slim", false, new String[] { "sleep", "3"}));
        }
        try (Engine engine = create()) {
            engine.podDelete("restart-pod");
        }
        try (Engine engine = create()) {
            assertTrue(engine.podCreate("restart-pod", "debian:stretch-slim", false, new String[] { "sleep", "3"}));
        }
        try (Engine engine = create()) {
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

        try (Engine engine = create()) {
            data = Data.secrets(name, "/etc/secrets");
            data.addUtf8("sub/renamed.txt", "blablub");
            data.define(engine);

            assertTrue(engine.secretList().containsKey(name));
            assertFalse(engine.podCreate(name, "debian:stretch-slim", false,
                    new String[] { "cat", "/etc/secrets/sub/renamed.txt" },"somehost", false, null,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.singletonList(data)));
            assertEquals("blablub", engine.podLogs(name));
            engine.podDelete(name);
            engine.secretDelete(name);
        }
    }

    @Test
    public void configMap() throws IOException {
        Map<String, String> data;
        final String name = "cm";

        data = Strings.toMap("test.yaml", "123", "sub-file", "foo");
        try (Engine engine = create()) {
            engine.configMapCreate(name, data);
            assertEquals(data, engine.configMapRead(name));
            engine.configMapDelete(name);;
        }
    }

    @Test
    public void configMapBinary() throws IOException {
        final String name = "cmbinary";
        Data data;

        try (Engine engine = create()) {
            data = Data.configMap(name, "/etc", true);
            data.addUtf8("test.yaml", "123");
            data.addUtf8("sub/file", "foo");
            data.define(engine);

            assertTrue(engine.configMapList().containsKey(name));

            assertFalse(engine.podCreate(name, "debian:stretch-slim", false,
                    new String[] { "cat", "/etc/test.yaml", "/etc/sub/file"}, "somehost", false, null,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.singletonList(data)));
            assertEquals("123foo", engine.podLogs(name));

            engine.podDelete(name);
            engine.configMapDelete(name);;
        }
    }

    @Test
    public void deployment() throws IOException {
        String name = "dpl";
        Map<String, DeploymentInfo> map;
        DeploymentInfo info;
        Map<String, PodInfo> pods;
        PodInfo pod;

        try (Engine engine = create()) {

            assertEquals(0, engine.deploymentList().size());
            engine.deploymentCreate(name, Strings.toMap("app", "foo"), Strings.toMap(), "debian:stretch-slim", true,
                    new String[] { "sleep", "1000" }, null, null, Strings.toMap("app", "foo"), Strings.toMap(),
                    Collections.emptyMap(), Collections.emptyList());

            map = engine.deploymentList();

            assertEquals(1, map.size());
            info = map.get(name);
            assertEquals(name, info.name);
            assertEquals(1, info.available);

            pods = engine.podList(Strings.toMap("app", "foo"));
            assertEquals(1, pods.size());
            pod = pods.values().iterator().next();
            assertTrue(pod.isRunning());

            engine.deploymentDelete(name);
            assertEquals(0, engine.deploymentList().size());

            engine.podAwait(pod.name, null);
        }
    }
}
