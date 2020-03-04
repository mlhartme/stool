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

import net.oneandone.stool.server.ArgumentException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.StatusException;
import net.oneandone.sushi.util.Strings;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class EngineIT {
    private static final World WORLD = World.createMinimal();

    private Engine create() throws IOException {
        return Engine.create("target/wire.log");

    }

    //-- pods + services

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
        String imageTag;
        String image;
        String message;
        String pod;

        message = UUID.randomUUID().toString();
        pod = "pod";
        try (Engine engine = create()) {
            engine.namespaceReset();

            imageTag = "someapp:1.0.0";
            image = engine.imageBuild(imageTag, Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD echo " + message + ";sleep 5\n"), false, null);
            assertNotNull(image);

            engine.podCreate(pod, imageTag);;
            assertEquals("Running", engine.podProbe(pod).phase);
            engine.podDelete(pod);
            assertNull(engine.podProbe(pod));
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
