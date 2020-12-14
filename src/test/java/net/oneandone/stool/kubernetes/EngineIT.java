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

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.util.Strings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EngineIT {
    private static final World WORLD = World.createMinimal();

    private static Engine create() throws IOException {
        return Engine.create(WORLD, "stool-engine-it" /* TODO */, Strings.toMap("origin", "net.oneandone.stool.engine-it"));
    }

    @BeforeAll
    public static void beforeAll() throws IOException {
        Engine engine;

        engine = create();
        engine.namespaceReset();
    }

    //-- pods

    @Test
    public void podEnv() throws IOException {
        String pod = "podenv";
        String output;

        try (Engine engine = create()) {
            assertFalse(engine.podCreate(pod,
                    new Engine.Container("env", "debian:stretch-slim", new String[] { "sh", "-c", "echo $foo $notfound $xxx" },
                            false, Strings.toMap("foo", "bar", "xxx", "after"), null, null, Collections.emptyMap()
                    ), Strings.toMap()));
            output = engine.podLogs(pod);
            assertEquals("bar after\n", output);
            engine.podDelete(pod);
        }
    }

    @Test
    public void podImplicitHostname() throws IOException {
        doHostnameTest("podimplicit", null, "podimplicit");
    }

    @Test
    public void podExplicitHostname() throws IOException {
        doHostnameTest("podexplicit", "ex", "ex");
    }

    private void doHostnameTest(String pod, String hostname, String expected) throws IOException {
        try (Engine engine = create()) {
            assertFalse(engine.podCreate(pod, new Engine.Container("debian:stretch-slim", "hostname"),
                    hostname, false, Strings.toMap()));
            assertEquals(false, engine.podContainerRunning(pod, "noname"));
            assertEquals(expected + "\n", engine.podLogs(pod));
            engine.podDelete(pod);
        }
    }

    @Test
    public void podLimit() throws IOException {
        final int limit = 1024*1024*7;
        String pod = "limit";

        try (Engine engine = create()) {
            engine.podCreate(pod, new Engine.Container("limit",
                            "debian:stretch-slim", new String[] { "sleep", "3" }, false, Collections.emptyMap(), null, limit, Collections.emptyMap()),
                    null, false, Collections.emptyMap());

            // TODO: only available when stats server is installed
            // assert on openshift.stats(pod)

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
            assertEquals(name, info.name);
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
    public void deployment() throws IOException {
        String name = "dpl";
        Map<String, DeploymentInfo> map;
        DeploymentInfo info;
        Map<String, PodInfo> pods;
        PodInfo pod;

        try (Engine engine = create()) {
            assertNull(engine.deploymentProbe("nosuchdeployment"));

            assertEquals(0, engine.deploymentList().size());
            engine.deploymentCreate(name, Strings.toMap("app", "foo"), Strings.toMap(),
                    new Engine.Container("main", "debian:stretch-slim", new String[] { "sleep", "1000" }, true,
                            Strings.toMap(), null, null, Collections.emptyMap()),
                    null, Strings.toMap("app", "foo"));
            engine.deploymentAwaitAvailable(name);

            map = engine.deploymentList();

            assertEquals(1, map.size());
            info = map.get(name);
            assertEquals(name, info.name);
            assertEquals(1, info.statusAvailable);

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
