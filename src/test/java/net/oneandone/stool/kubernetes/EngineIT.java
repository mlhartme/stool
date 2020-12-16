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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EngineIT {
    private static final World WORLD = World.createMinimal();

    private static Engine create() throws IOException {
        return Engine.create(WORLD, "stool-engine-it" /* TODO */);
    }

    @BeforeAll
    public static void beforeAll() throws IOException {
        Engine engine;

        engine = create();
        engine.namespaceReset();
    }

    //-- pods

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
            assertFalse(engine.podCreate(pod, "debian:stretch-slim", new String[] { "hostname" },
                    hostname, false, Strings.toMap()));
            assertEquals(false, engine.podContainerRunning(pod, "noname"));
            assertEquals(expected + "\n", engine.podLogs(pod));
            engine.podDelete(pod);
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
                    "debian:stretch-slim", new String[] { "sleep", "1000" },
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
