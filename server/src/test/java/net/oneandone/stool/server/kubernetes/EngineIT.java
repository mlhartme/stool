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
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.StatusException;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotEquals;

public class EngineIT {
    private static final World WORLD = World.createMinimal();

    private Engine create() throws IOException {
        return Engine.create("target/wire.log");
    }

    @BeforeClass
    public static void beforeClass() throws IOException {
        Engine engine;

        engine = Engine.create("target/wipe-ns.log");
        engine.namespaceReset();
    }


    //-- images

    @Test(expected = ArgumentException.class)
    public void rejectBuildWithUppercaseTag() throws IOException {
        try (Engine engine = create()) {
            engine.imageBuild("tagWithUpperCase", Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD ls -la /\n"), false, null);
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
    public void invalidDockerfile() throws IOException {
        try (Engine engine = create()) {
            engine.imageBuild("sometag", Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nls -la /dev/fuse\n"), false, null);
            fail();
        } catch (StatusException e) {
            assertEquals(400, e.getStatusLine().code);
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
    public void imageLabels() throws IOException {
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

    @Test
    public void cmdNotFound() throws IOException {
        String image = "stooltest";
        String pod = "cmdnotfound";

        try (Engine engine = create()) {
            engine.imageBuildWithOutput(image, dockerfile("FROM debian:stretch-slim\nCMD [\"/nosuchcmd\"]\n"));
            try {
                engine.podCreate(pod, image);
                fail();
            } catch (IOException e) {
                assertEquals("create-pod failed: Failed", e.getMessage());
                engine.podDelete(pod);
               // ok
            }
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
        final String imageTag = "foobla";
        final String name = "pod";
        String image;
        Collection<PodInfo> lst;
        PodInfo info;
        String container;

        try (Engine engine = create()) {
            assertEquals(Collections.emptyMap(), engine.podList());
            image = engine.imageBuild(imageTag, Collections.emptyMap(), Collections.emptyMap(),
                    dockerfile("FROM debian:stretch-slim\nCMD echo ho\n"), false, null);
            assertFalse(engine.podCreate(name, imageTag, "foo", "bar"));
            lst = engine.podList().values();
            assertEquals(1, lst.size());
            info = lst.iterator().next();
            assertEquals(name, info.name);
            assertEquals("Succeeded", info.phase);
            assertEquals(Strings.toMap("foo", "bar"), info.labels);
            container = info.containerId;
            assertEquals(Engine.Status.EXITED, engine.containerStatus(container));

            engine.podDelete(name);
            assertEquals(Collections.emptyMap(), engine.containerListForImage(image));
            assertEquals(0, engine.podList().size());
            engine.imageRemove(image, true);
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
        JsonObject obj;
        Stats stats;

        labels = Strings.toMap("stooltest", UUID.randomUUID().toString());
        try (Engine engine = create()) {
            assertTrue(engine.imageList(labels).isEmpty());
            engine.imageBuild("some:tag", Collections.emptyMap(), labels, dockerfile("FROM debian:stretch-slim\nRUN touch abc\nCMD sleep 5\n"), false, null);
            ids = new ArrayList<>(engine.imageList(labels).keySet());
            assertEquals(1, ids.size());
            image = ids.get(0);
            assertTrue(engine.containerListForImage(image).isEmpty());
            assertTrue(engine.containerList("stooltest").isEmpty());
            engine.podCreate(pod, "some:tag", null,true, null, Strings.toMap("containerLabel", "bla"), Collections.emptyMap(), Collections.emptyMap());
            container = engine.podProbe(pod).containerId;
            assertEquals(Engine.Status.RUNNING, engine.containerStatus(container));

            stats = engine.containerStats(container);
            assertEquals(0, stats.cpu);

            obj = engine.containerInspect(container, false).get("Config").getAsJsonObject().get("Labels").getAsJsonObject();
            assertEquals(obj.get("stooltest"), new JsonPrimitive(labels.get("stooltest")));
            assertNull(obj.get("containerLabel"));
            map = engine.containerListForImage(image);
            assertEquals(1, map.size());
            assertTrue(map.containsKey(container));

            assertEquals(Arrays.asList(container), new ArrayList<>(engine.containerList("stooltest").keySet()));
            map = engine.containerListForImage(image);
            assertEquals(1, map.size());
            assertTrue(map.containsKey(container));
            assertEquals(Engine.Status.RUNNING, map.get(container).state);

            engine.containerStop(container, 5);
            Thread.sleep(2500);

            map = engine.containerListForImage(image);
            containerHealed = map.keySet().iterator().next();
            assertNotEquals(container, containerHealed);
            assertEquals(Engine.Status.RUNNING, engine.containerStatus(containerHealed));

            assertEquals(Arrays.asList(containerHealed), new ArrayList<>(engine.containerListForImage(image).keySet()));

            engine.podDelete(pod);

            assertTrue(engine.containerListForImage(image).isEmpty());
            engine.imageRemove(image, false);
            assertEquals(new HashMap<>(), engine.imageList(labels));
        }
    }


    @Test
    public void podRestart() throws IOException {
        String image;
        String message;

        message = UUID.randomUUID().toString();
        try (Engine engine = create()) {
            image = engine.imageBuild("restart:tag", Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD echo " + message + "; sleep 3\n"), false, null);
            engine.podCreate("restart-pod", "restart:tag");
        }
        try (Engine engine = Engine.create()) {
            engine.podDelete("restart-pod");
            engine.imageRemove(image, false);
        }
        try (Engine engine = Engine.create()) {
            image = engine.imageBuild("restart:tag", Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD echo " + message + "; sleep 3\n"), false, null);
            engine.podCreate("restart-pod", "restart:tag");
        }
        try (Engine engine = Engine.create()) {
            engine.podDelete("restart-pod");
            engine.imageRemove(image, false);
        }
    }

    @Test
    public void podEnv() throws IOException {
        String image = "stooltest";
        String pod = "podenv";
        String output;
        String container;

        try (Engine engine = create()) {
            output = engine.imageBuildWithOutput(image, dockerfile("FROM debian:stretch-slim\nCMD echo $foo $notfound $xxx\n"));
            assertNotNull(output);
            assertFalse(engine.podCreate(pod, image, Strings.toMap(), Strings.toMap("foo", "bar", "xxx", "after")));
            container = engine.podProbe(pod).containerId;
            assertEquals("bar after\n", engine.containerLogs(container));
            engine.podDelete(pod);
            engine.imageRemove(image, true);
        }
    }

    @Test
    public void podImplicittHostname() throws IOException, InterruptedException {
        doHostnameTest("podimplicit", null, "podimplicit");
    }

    @Test
    public void podExplicitHostname() throws IOException, InterruptedException {
        doHostnameTest("podexplicit", "ex", "ex");
    }

    private void doHostnameTest(String pod, String hostname, String expected) throws IOException, InterruptedException {
        String image = "hostname";
        String output;
        String container;

        try (Engine engine = create()) {
            output = engine.imageBuildWithOutput(image, dockerfile("FROM debian:stretch-slim\nRUN echo pod\nCMD hostname\n"));
            assertNotNull(output);
            engine.podCreate(pod, image, hostname, false, null, Strings.toMap(), Strings.toMap(), Strings.toMap());
            container = engine.podProbe(pod).containerId;
            Thread.sleep(500);
            assertEquals(Engine.Status.EXITED, engine.containerStatus(container));
            assertEquals(expected + "\n", engine.containerLogs(container));
            engine.podDelete(pod);
            // TODO: causes conflict ...
            //   engine.imageRemove(image, false);
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
        try (Engine engine = create()) {
            output = engine.imageBuildWithOutput(image, dockerfile("FROM debian:stretch-slim\nCMD ls " + file.getAbsolute() + "\n"));
            assertNotNull(output);

            assertFalse(engine.podCreate(pod, image, null,false, null, Collections.emptyMap(), Collections.emptyMap(),
                    Collections.singletonMap(home.getAbsolute(), home.getAbsolute())));
            output = engine.containerLogs(engine.podProbe(pod).containerId);
            assertTrue(output.contains(file.getAbsolute()));
            engine.podDelete(pod);

            engine.imageRemove(image, true);
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
        try (Engine engine = create()) {
            engine.imageBuild(image, Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD echo " + message + "; sleep 3\n"), false, null);
            engine.podCreate(pod, image, null,false, limit, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
            container = engine.podProbe(pod).containerId;
            stats = engine.containerStats(container);
            assertEquals(limit, stats.memoryLimit);
            assertTrue(stats.memoryUsage <= stats.memoryLimit);
            engine.podDelete(pod);
            engine.imageRemove(image, false);
        }
    }

    //-- services

    @Test
    public void services() throws IOException {
        final String name = "service";

        try (Engine engine = create()) {
            assertEquals(0, engine.serviceList().size());
            engine.serviceCreate(name, 30001, 8080);
            assertEquals(Arrays.asList(name), new ArrayList<>(engine.serviceList()));
            engine.serviceDelete(name);
            assertEquals(0, engine.serviceList().size());
        }
    }
}
