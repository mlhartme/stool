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
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
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

    @Test
    public void copyImage() throws IOException {
        FileNode tmp;

        try (Engine engine = create()) {
            tmp = WORLD.getTemp().createTempDirectory().deleteDirectory();
            engine.copyImage("debian:buster-slim", "/etc", tmp);
            assertTrue(tmp.find("**/*").size() > 1);
            tmp.deleteTree();
        }
    }

    @Test
    public void podExec() throws IOException {
        String name = "pod-exec";
        String output;

        try (Engine engine = create()) {
            engine.podCreate(name, "debian:buster-slim", 0, "sleep", "3600");
            output = engine.podExec(name, "noname", "echo", "hi");
            engine.podDeleteBg(name);
        }
        assertEquals("hi\n", output);
    }

    @Test
    public void podImplicitHostname() throws IOException {
        String name = "pod-implicit";

        try (Engine engine = create()) {
            engine.podDeleteOpt(name);
            assertFalse(engine.isOpenShift());
            assertFalse(engine.podCreate(name, "debian:stretch-slim", 0, "hostname"));
            assertEquals(false, engine.podContainerRunning(name, "noname"));
            assertEquals(name + "\n", engine.podLogs(name));
            engine.podDeleteBg(name);
        }
    }

    @Test
    public void deployment() throws IOException {
        String name = "dpl";
        Map<String, DeploymentInfo> map;
        DeploymentInfo info;
        Map<String, PodInfo> pods;
        PodInfo pod;
        int initialDeployments;

        try (Engine engine = create()) {
            engine.deploymentDeleteOpt(name);
            engine.deploymentAwaitGone(name);

            assertNull(engine.deploymentProbe("nosuchdeployment"));

            initialDeployments = engine.deploymentList().size();
            engine.deploymentCreate(name, Strings.toMap("app", "foo"), Strings.toMap(),
                    "debian:stretch-slim", new String[] { "sleep", "1000" },
                    null, Strings.toMap("app", "foo"));
            engine.deploymentAwaitAvailable(name);

            map = engine.deploymentList();

            assertEquals(initialDeployments + 1, map.size());
            info = map.get(name);
            assertEquals(name, info.name);
            assertEquals(1, info.statusAvailable);

            pods = engine.podList(Strings.toMap("app", "foo"));
            assertEquals(1, pods.size());

            pod = pods.values().iterator().next();
            assertTrue(pod.isRunning());

            engine.deploymentDelete(name);
            assertEquals(initialDeployments, engine.deploymentList().size());
            engine.podAwait(pod.name, (String) null);
        }
    }
}
