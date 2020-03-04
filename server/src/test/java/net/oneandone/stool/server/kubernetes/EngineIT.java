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
package net.oneandone.stool.server.kubernetes;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.oneandone.stool.server.ArgumentException;
import net.oneandone.sushi.fs.FileNotFoundException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.StatusException;
import net.oneandone.sushi.util.Strings;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EngineIT {
    private static final World WORLD = World.createMinimal();

    private Engine create() throws IOException {
        return Engine.create("target/wire.log");

    }

    //-- pods

    @Test
    public void pods() throws IOException {
        final String name = "pod";
        Collection<PodInfo> lst;
        PodInfo info;

        try (Engine engine = Engine.create()) {
            engine.namespaceReset();
            assertEquals(0, engine.podList().size());
            engine.podCreate(name, "contargo.server.lan/cisoops-public/hellowar:1.0.0", "foo", "bar");
            lst = engine.podList().values();
            assertEquals(1, lst.size());
            info = lst.iterator().next();
            assertEquals(name, info.name);
            assertEquals(Strings.toMap("foo", "bar"), info.labels);
            engine.podDelete(name);
            assertEquals(0, engine.podList().size());
        }
    }

    @Test
    public void hello() throws IOException {
        final int port = 30003;
        final String name = "xyz";
        Map<String, String> labels;

        labels = Strings.toMap("foo", "bar");
        try (Engine engine = Engine.create()) {
            engine.namespaceReset();
            assertEquals(0, engine.podList().size());
            engine.podCreate(name, "contargo.server.lan/cisoops-public/hellowar:1.0.0", labels);
            engine.serviceCreate(name, port, 8080, labels);
            /* TODO
            try {
                System.out.println("waiting ...");
                Thread.sleep(20000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(engine.world.validNode("http://localhost:" + port + "/?cmd=info").readString());
             */
            engine.serviceDelete(name);
            engine.podDelete(name);
            assertEquals(0, engine.podList().size());
        }
    }

    @Test
    public void services() throws IOException {
        final String name = "service";

        try (Engine engine = Engine.create()) {
            engine.namespaceReset();
            assertEquals(0, engine.serviceList().size());
            engine.serviceCreate(name, 30001, 8080);
            assertEquals(Arrays.asList(name), new ArrayList<>(engine.serviceList()));
            engine.serviceDelete(name);
            assertEquals(0, engine.serviceList().size());
        }
    }

    @Test
    public void turnaround() throws IOException {
        String image;
        String message;
        String output;
        String container;
        Stats stats;

        message = UUID.randomUUID().toString();

        try (Engine engine = create()) {
            image = engine.imageBuild("sometag", Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD echo " + message + ";sleep 5\n"), false, null);
            assertNotNull(image);

            container = engine.containerCreate(null, image, "foo", null, false,
                    null, null, null, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
            assertNotNull(container);
            assertEquals(Engine.Status.CREATED, engine.containerStatus(container));
            assertNull(engine.containerStats(container));
            engine.containerStart(container);
            stats = engine.containerStats(container);
            assertEquals(0, stats.cpu);
            assertEquals(Engine.Status.RUNNING, engine.containerStatus(container));
            assertEquals(0, engine.containerWait(container));
            assertEquals(Engine.Status.EXITED, engine.containerStatus(container));
            assertNull(engine.containerStats(container));
            output = engine.containerLogs(container);
            assertTrue(output + " vs" + message, output.contains(message));
            try {
                engine.containerStop(container, 300);
                fail();
            } catch (StatusException e) {
                assertEquals(304, e.getStatusLine().code);
            }
            assertNull(engine.containerStats(container));
            assertEquals(Engine.Status.EXITED, engine.containerStatus(container));
            engine.containerRemove(container);
            try {
                engine.containerStatus(container);
                fail();
            } catch (FileNotFoundException e) {
                // ok
            }
            try {
                assertNull(engine.containerStats(container));
                fail();
            } catch (FileNotFoundException e) {
                // ok
            }
            engine.imageRemove(image, false);
        }
    }

    //-- images

    @Test(expected = ArgumentException.class)
    public void rejectBuildWithUppercaseTag() throws IOException {
        try (Engine engine = create()) {
            engine.imageBuild("tagWithUpperCase", Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD ls -la /\n"), false, null);
        }
    }

    @Test
    public void invalidDockerfile() throws IOException {
        try (Engine engine = create()) {
            engine.imageBuild("sometag", Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nls -la /dev/fuse\n"), false, null);
            fail();
        } catch (StatusException e) {
            assertEquals(400, e.getStatusLine().code);
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
}
