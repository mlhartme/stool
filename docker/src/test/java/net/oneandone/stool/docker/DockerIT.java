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
import net.oneandone.stool.docker.BuildError;
import net.oneandone.stool.docker.Docker;
import net.oneandone.stool.docker.ImageInfo;
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

public class DockerIT {
    private static final World WORLD = World.createMinimal();

    private Docker create() throws IOException {
        return Docker.create("target/wire.log");
    }

    //-- images

    @Test(expected = ArgumentException.class)
    public void rejectBuildWithUppercaseTag() throws IOException {
        try (Docker docker = create()) {
            docker.imageBuild("tagWithUpperCase", Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nCMD ls -la /\n"), false, null);
        }
    }

    @Test
    public void runFailure() throws IOException {
        String image = "stooltest";

        try (Docker engine = create()) {
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
        try (Docker engine = create()) {
            engine.imageBuild("sometag", Collections.emptyMap(), Collections.emptyMap(), dockerfile("FROM debian:stretch-slim\nls -la /dev/fuse\n"), false, null);
            fail();
        } catch (StatusException e) {
            assertEquals(400, e.getStatusLine().code);
        }
    }

    @Test
    public void copy() throws IOException {
        String image = "stooltest";

        try (Docker engine = create()) {
            engine.imageBuildWithOutput(image, WORLD.guessProjectHome(getClass()).join("src/test/docker"));
            engine.imageRemove(image, false);
        }
    }

    @Test
    public void copyFailure() throws IOException {
        String image = "stooltest";

        try (Docker engine = create()) {
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

        try (Docker engine = create()) {
            output = new StringWriter();
            image = engine.imageBuild("labeltest", Collections.emptyMap(), labels, dockerfile("FROM debian:stretch-slim\nCMD [\"echo\", \"hi\", \"/\"]\n"),
                    false, output);
            info = engine.imageList().get(image);
            assertEquals(labels, info.labels);
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
