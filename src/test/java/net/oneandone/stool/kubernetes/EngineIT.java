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

import com.fasterxml.jackson.databind.ObjectMapper;
import net.oneandone.stool.util.ITProperties;
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
    private static final World WORLD;

    static {
        try {
            WORLD = World.create();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Engine create() throws IOException {
        return Engine.createLocal(new ObjectMapper(), ITProperties.load(WORLD).kubernetes);
    }

    @BeforeAll
    public static void beforeAll() throws IOException {
        Engine engine;

        engine = create();
        engine.namespaceReset();
    }

    @Test
    public void podImplicitHostname() throws IOException {
        try (Engine engine = create()) {
            assertFalse(engine.isOpenShift());
            assertFalse(engine.podCreate("podimplicit", "debian:stretch-slim", new String[] { "hostname" },
                    null, false, Strings.toMap()));
            assertEquals(false, engine.podContainerRunning("podimplicit", "noname"));
            assertEquals("podimplicit" + "\n", engine.podLogs("podimplicit"));
            engine.podDelete("podimplicit");
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

            engine.podAwait(pod.name, (String) null);
        }
    }
}
