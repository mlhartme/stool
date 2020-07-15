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
package net.oneandone.stool.docker;

import net.oneandone.inline.ArgumentException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.StatusException;
import net.oneandone.sushi.util.Strings;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DaemonIT {
    private static final World WORLD = World.createMinimal();

    private Daemon create() throws IOException {
        return Daemon.create("target/wire.log");
    }

    //-- images

    @Test(expected = ArgumentException.class)
    public void rejectBuildWithUppercaseTag() throws IOException {
        try (Daemon docker = create()) {
            docker.imageBuild("tagWithUpperCase", Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD ls -la /\n"), false, null);
        }
    }

    @Test
    public void runFailure() throws IOException {
        String image = "stooltest";

        try (Daemon docker = create()) {
            docker.imageBuildWithOutput(image, dockerfile("FROM debian:stretch-slim\nRUN /bin/nosuchcmd\nCMD [\"echo\", \"hi\", \"/\"]\n"));
            fail();
        } catch (BuildError e) {
            // ok
            assertNotNull("", e.error);
            assertEquals(127, e.details.get("code").getAsInt());
            assertNotNull("", e.output);
        }
    }

    @Test
    public void copy() throws IOException {
        String image = "stooltest";

        try (Daemon docker = create()) {
            docker.imageBuildWithOutput(image, WORLD.guessProjectHome(getClass()).join("src/test/docker"));
            docker.imageRemove(image, false);
        }
    }

    @Test
    public void copyFailure() throws IOException {
        String image = "stooltest";

        try (Daemon docker = create()) {
            docker.imageBuildWithOutput(image, dockerfile("FROM debian:stretch-slim\nCOPY nosuchfile /nosuchfile\nCMD [\"echo\", \"hi\", \"/\"]\n"));
            fail();
        } catch (BuildError e) {
            // ok
            assertTrue(e.error, e.error.contains("nosuchfile"));
            assertNotNull("", e.output);
        }
    }

    @Test
    public void imageLabels() throws IOException {
        Map<String, String> labels = Strings.toMap("a", "b", "1", "234");
        StringWriter output;
        String image;
        ImageInfo info;

        try (Daemon docker = create()) {
            output = new StringWriter();
            image = docker.imageBuild("labeltest", Collections.emptyMap(), labels, dockerfile("FROM debian:stretch-slim\nCMD [\"echo\", \"hi\", \"/\"]\n"),
                    false, output);
            info = docker.imageList().get(image);
            assertEquals(labels, info.labels);
        }
    }

    public static FileNode dockerfile(String dockerfile, FileNode ... extras) throws IOException {
        FileNode dir;

        dir = WORLD.getTemp().createTempDirectory();
        dir.join("Dockerfile").writeString(dockerfile);
        for (FileNode extra : extras) {
            extra.copyFile(dir.join(extra.getName()));
        }
        return dir;
    }
}
