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
import static org.junit.Assert.assertNotEquals;

public class EngineIT {
    private static final World WORLD = World.createMinimal();

    private Engine create() throws IOException {
        return Engine.create("target/wire.log");

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

    //-- pods + services

    @Test
    public void pods() throws IOException {
        final String imageTag = "foobla";
        final String name = "pod";
        Collection<PodInfo> lst;
        PodInfo info;

        try (Engine engine = Engine.create()) {
            engine.namespaceReset();
            assertEquals(0, engine.podList().size());
            assertNotNull(engine.imageBuild(imageTag, Collections.emptyMap(), Collections.emptyMap(),
                    dockerfile("FROM debian:stretch-slim\nCMD echo ho;sleep 5\n"), false, null));
            engine.podCreate(name, imageTag, "foo", "bar");
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
    public void healing() throws IOException, InterruptedException {
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
            engine.imageBuild("some:tag", Collections.emptyMap(), labels, dockerfile("FROM debian:stretch-slim\nRUN touch abc\nCMD sleep 3600\n"), false, null);
            ids = new ArrayList<>(engine.imageList(labels).keySet());
            assertEquals(1, ids.size());
            image = ids.get(0);
            assertTrue(engine.containerListForImage(image).isEmpty());
            assertTrue(engine.containerList("stooltest").isEmpty());
            engine.podCreate(pod, "some:tag", true, null, Strings.toMap("containerLabel", "bla"), Collections.emptyMap(), Collections.emptyMap());
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
            image = engine.imageBuild("restart:tag", Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD echo " + message + ";sleep 5\n"), false, null);
            engine.podCreate("restart-pod", "restart:tag");
        }
        try (Engine engine = create()) {
            engine.podDelete("restart-pod");
            engine.imageRemove(image, false);
        }
        try (Engine engine = create()) {
            image = engine.imageBuild("restart:tag", Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD echo " + message + ";sleep 5\n"), false, null);
            engine.podCreate("restart-pod", "restart:tag");
        }
        try (Engine engine = create()) {
            engine.podDelete("restart-pod");
            engine.imageRemove(image, false);
        }
    }

    @Test
    public void podEnv() throws IOException, InterruptedException {
        String image = "stooltest";
        String pod = "podenv";
        String output;
        String container;

        try (Engine engine = create()) {
            output = engine.imageBuildWithOutput(image, dockerfile("FROM debian:stretch-slim\nCMD echo $foo $notfound $xxx; sleep 60\n"));
            assertNotNull(output);
            engine.podCreate(pod, image, Strings.toMap(), Strings.toMap("foo", "bar", "xxx", "after"));
            container = engine.podProbe(pod).containerId;
            assertEquals(Engine.Status.RUNNING, engine.containerStatus(container));
            Thread.sleep(1000);
            assertEquals("bar after\n", engine.containerLogs(container));
            engine.podDelete(pod);
            engine.imageRemove(image, false);
        }
    }

    @Test
    public void podMount() throws IOException, InterruptedException {
        FileNode home;
        FileNode file;
        String image = "stooltest";
        String pod = "bindmount";
        String output;

        home = WORLD.getHome();
        file = home.createTempFile();
        try (Engine engine = create()) {
            output = engine.imageBuildWithOutput(image, dockerfile("FROM debian:stretch-slim\nCMD ls " + file.getAbsolute() + "; sleep 60\n"));
            assertNotNull(output);

            engine.podCreate(pod, image, false,null, Collections.emptyMap(), Collections.emptyMap(),
                    Collections.singletonMap(home.getAbsolute(), home.getAbsolute()));
            Thread.sleep(1000);
            output = engine.containerLogs(engine.podProbe(pod).containerId);
            assertTrue(output.contains(file.getAbsolute()));
            engine.podDelete(pod);

            engine.imageRemove(image, false);
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
            engine.imageBuild(image, Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD echo " + message + ";sleep 60\n"), false, null);
            engine.podCreate(pod, image, false, limit, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
            container = engine.podProbe(pod).containerId;
            stats = engine.containerStats(container);
            assertEquals(limit, stats.memoryLimit);
            assertTrue(stats.memoryUsage <= stats.memoryLimit);
            engine.podDelete(pod);
            engine.imageRemove(image, false);
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
        String imageName;
        String image;
        String message;
        String pod;

        message = UUID.randomUUID().toString();
        pod = "pod";
        try (Engine engine = create()) {
            engine.namespaceReset();

            imageName = "turnaround";
            image = engine.imageBuild(imageName, Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD echo " + message + ";sleep 5\n"), false, null);
            assertNotNull(image);

            engine.podCreate(pod, imageName);
            assertEquals("Running", engine.podProbe(pod).phase);
            engine.podDelete(pod);
            assertNull(engine.podProbe(pod));
            engine.imageRemove(image, false);
        }
    }
}
