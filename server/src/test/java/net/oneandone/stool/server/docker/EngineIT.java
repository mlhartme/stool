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
package net.oneandone.stool.server.docker;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EngineIT {
    private static final World WORLD = World.createMinimal();

    private Engine create() throws IOException {
        return Engine.create("target/wire.log");

    }
    @Test
    public void deviceFuse() throws IOException, InterruptedException {
        String image;
        String container;

        try (Engine engine = create()) {
            image = engine.imageBuild("sometag", Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD ls -la /dev/fuse\n"), false, null);
            container = engine.containerCreate(image, "somehost");
            engine.containerStart(container);
            Thread.sleep(1000);
            engine.containerRemove(container);
            engine.imageRemove(image, false);
        }
    }

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

    @Test
    public void list() throws IOException, InterruptedException {
        Map<String, String> labels;
        List<String> ids;
        String image;
        String container;
        Map<Integer, String> ports;
        Map<String, ContainerInfo> map;
        JsonObject obj;
        JsonObject cmp;

        labels = Strings.toMap("stooltest", UUID.randomUUID().toString());
        try (Engine engine = create()) {
            assertTrue(engine.imageList(labels).isEmpty());
            engine.imageBuild("sometag", Collections.emptyMap(), labels, dockerfile("FROM debian:stretch-slim\nRUN touch abc\nCMD sleep 2\n"), false, null);
            ids = new ArrayList<>(engine.imageList(labels).keySet());
            assertEquals(1, ids.size());
            image = ids.get(0);
            assertTrue(containerListForImage(engine, image).isEmpty());
            assertTrue(engine.containerListRunning("stooltest").isEmpty());
            ports = new HashMap<>();
            ports.put(1301, "1302");
            container = engine.containerCreate(null, image, "somehost", null, false, null, null, null,
                    Strings.toMap("containerLabel", "bla"), Collections.emptyMap(), Collections.emptyMap(), ports);

            assertEquals(Engine.Status.CREATED, engine.containerStatus(container));
            obj = engine.containerInspect(container, false).get("Config").getAsJsonObject().get("Labels").getAsJsonObject();
            cmp = new JsonObject();
            cmp.add("stooltest", new JsonPrimitive(labels.get("stooltest")));
            cmp.add("containerLabel", new JsonPrimitive("bla"));
            assertEquals(cmp, obj);
            assertTrue(engine.containerListRunning("stooltest").isEmpty());
            map = containerListForImage(engine, image);
            assertEquals(1, map.size());
            assertTrue(map.containsKey(container));
            assertTrue(map.get(container).ports.isEmpty()); // no ports allocated until the container is actually started

            engine.containerStart(container);

            assertEquals(Engine.Status.RUNNING, engine.containerStatus(container));
            assertEquals(Arrays.asList(container), new ArrayList<>(engine.containerListRunning("stooltest").keySet()));
            map = containerListForImage(engine, image);
            assertEquals(1, map.size());
            assertTrue(map.containsKey(container));
            assertEquals(ports, convert(map.get(container).ports));

            Thread.sleep(2500);

            assertEquals(Engine.Status.EXITED, engine.containerStatus(container));

            map = containerListForImage(engine, image);
            assertEquals(1, map.size());
            assertTrue(map.containsKey(container));
            assertTrue(map.get(container).ports.isEmpty()); // ports free again

            assertEquals(Arrays.asList(container), new ArrayList<>(containerListForImage(engine, image).keySet()));
            engine.containerRemove(container);
            assertTrue(containerListForImage(engine, image).isEmpty());
            engine.imageRemove(image, false);
            assertEquals(new HashMap<>(), engine.imageList(labels));
        }
    }

    private Map<String, ContainerInfo> containerListForImage(Engine engine, String image) throws IOException {
        return engine.containerList("{\"ancestor\" : [\"" + image + "\"] }", true);
    }

    private static Map<Integer, String> convert(Map<Integer, Integer> map) {
        Map<Integer, String> result;

        result = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            result.put(entry.getKey(), Integer.toString(entry.getValue()));
        }
        return result;
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
            assertNotEquals(0, engine.containerStartedAt(container));
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

    @Test
    public void restart() throws IOException {
        String image;
        String message;
        String container;

        message = UUID.randomUUID().toString();

        try (Engine engine = create()) {
            image = engine.imageBuild("sometag", Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD echo " + message + ";sleep 5\n"), false, null);
            container = engine.containerCreate(null, image, "foo", null, false,
                    null, null, null, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
            engine.containerStart(container);
        }
        try (Engine engine = create()) {
            engine.containerStop(container, 60);

            engine.containerRemove(container);
            engine.imageRemove(image, false);
        }
        try (Engine engine = create()) {
            image = engine.imageBuild("sometag", Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD echo " + message + ";sleep 5\n"), false, null);
            container = engine.containerCreate(null, image, "foo", null, false,
                    null, null, null, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
            engine.containerStart(container);
        }
        try (Engine engine = create()) {
            engine.containerStop(container, 60);

            engine.containerRemove(container);
            engine.imageRemove(image, false);
        }
    }

    /** I've seen this check fail with strange errors on Manjaro Linux */
    @Test
    public void limit() throws IOException {
        final long limit = 1024*1024*5;
        Stats stats;
        String image;
        String message;
        String container;

        message = UUID.randomUUID().toString();
        try (Engine engine = create()) {
            image = engine.imageBuild("sometag", Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD echo " + message + ";sleep 5\n"), false, null);
            container = engine.containerCreate(null, image, "foo", null, false,
                    limit, null, null, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
            engine.containerStart(container);
            stats = engine.containerStats(container);
            assertEquals(limit, stats.memoryLimit);
            assertTrue(stats.memoryUsage <= stats.memoryLimit);
            engine.containerStop(container, 60);

            engine.containerRemove(container);
            engine.imageRemove(image, false);
        }
    }

    @Test
    public void stop() throws IOException {
        String image = "stooltest";
        String output;
        String container;
        long duration;

        try (Engine engine = create()) {
            output = engine.imageBuildWithOutput(image, dockerfile("FROM debian:stretch-slim\nCMD [\"/bin/sleep\", \"30\"]\n"));
            assertNotNull(output);

            container = engine.containerCreate(null, image, "foo", null, false, null, /*"SIGQUIT"*/ null, 3,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
            engine.containerStart(container);
            assertEquals(Engine.Status.RUNNING, engine.containerStatus(container));
            duration = System.currentTimeMillis();
            engine.containerStop(container, 6);
            duration = System.currentTimeMillis() - duration;
            assertTrue("about 6 seconds expected, took " + duration, duration >= 6000 && duration < 8000);
            assertEquals(Engine.Status.EXITED, engine.containerStatus(container));
            assertNotNull(engine.containerLogs(container));
            engine.containerRemove(container);
            engine.imageRemove(image, false);
        }
    }

    @Test
    public void env() throws IOException, InterruptedException {
        String image = "stooltest";
        String output;
        String container;

        try (Engine engine = create()) {
            output = engine.imageBuildWithOutput(image, dockerfile("FROM debian:stretch-slim\nCMD echo $foo $notfound $xxx\n"));
            assertNotNull(output);
            container = engine.containerCreate(null, image, "foo", null, false, null, /*"SIGQUIT"*/ null, 3,
                    Collections.emptyMap(), Strings.toMap("foo", "bar", "xxx", "after"), Collections.emptyMap(), Collections.emptyMap());
            engine.containerStart(container);
            assertEquals(Engine.Status.RUNNING, engine.containerStatus(container));
            Thread.sleep(1000);
            assertEquals(Engine.Status.EXITED, engine.containerStatus(container));
            assertEquals("bar after\n", engine.containerLogs(container));
            engine.containerRemove(container);
            engine.imageRemove(image, false);
        }
    }

    @Test
    public void bindMount() throws IOException {
        FileNode home;
        FileNode file;
        String image = "stooltest";
        String output;
        String container;

        home = WORLD.getHome();
        file = home.createTempFile();
        try (Engine engine = create()) {
            output = engine.imageBuildWithOutput(image, dockerfile("FROM debian:stretch-slim\nCMD ls " + file.getAbsolute() + "\n"));
            assertNotNull(output);

            container = engine.containerCreate(null, image, "foo", null, false, null, null, null,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.singletonMap(home, home.getAbsolute()), Collections.emptyMap());
            assertNotNull(container);
            assertEquals(Engine.Status.CREATED, engine.containerStatus(container));
            engine.containerStart(container);
            assertEquals(0, engine.containerWait(container));
            output = engine.containerLogs(container);
            assertTrue(output.contains(file.getAbsolute()));
            engine.containerRemove(container);

            engine.imageRemove(image, false);
        }
    }

    @Test
    public void runFailure() throws IOException {
        String image = "stooltest";

        try (Engine engine = create()) {
            engine.imageBuildWithOutput(image, dockerfile("FROM debian:stretch-slim\nRUN /bin/nosuchcmd\nCMD [\"echo\", \"hi\", \"/\"]\n"));
            fail();
        } catch (BuildError e) {
            // ok
            assertNotNull("", e.error);
            assertEquals(127, e.details.get("code").getAsInt());
            assertNotNull("", e.output);
        }
    }

    @Test
    public void cmdNotFound() throws IOException {
        String image = "stooltest";
        String container;

        try (Engine engine = create()) {
            engine.imageBuildWithOutput(image, dockerfile("FROM debian:stretch-slim\nCMD [\"/nosuchcmd\"]\n"));
            container = engine.containerCreate(image, "foo");
            assertNotNull(container);
            assertEquals(Engine.Status.CREATED, engine.containerStatus(container));
            try {
                engine.containerStart(container);
                fail();
            } catch (StatusException e) {
                assertEquals(400, e.getStatusLine().code);
            }
        }
    }

    @Test
    public void copy() throws IOException {
        String image = "stooltest";

        try (Engine engine = create()) {
            engine.imageBuildWithOutput(image, WORLD.guessProjectHome(getClass()).join("src/test/docker"));
            engine.imageRemove(image, false);
        }
    }

    @Test
    public void copyFailure() throws IOException {
        String image = "stooltest";

        try (Engine engine = create()) {
            engine.imageBuildWithOutput(image, dockerfile("FROM debian:stretch-slim\ncopy nosuchfile /nosuchfile\nCMD [\"echo\", \"hi\", \"/\"]\n"));
            fail();
        } catch (BuildError e) {
            // ok
            assertTrue(e.error.contains("COPY failed"));
            assertNotNull("", e.output);
        }
    }

    @Test
    public void labels() throws IOException {
        Map<String, String> labels = Strings.toMap("a", "b", "1", "234");
        StringWriter output;
        String image;
        ImageInfo info;

        try (Engine engine = create()) {
            output = new StringWriter();
            image = engine.imageBuild("labeltest", Collections.emptyMap(), labels, dockerfile("FROM debian:stretch-slim\nCMD [\"echo\", \"hi\", \"/\"]\n"),
                    false, output);
            info = engine.imageList().get(image);
            assertEquals(labels, info.labels);
        }
    }

    //--

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
