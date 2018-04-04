package net.oneandone.stool.docker;

import net.oneandone.sushi.fs.FileNotFoundException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.StatusException;
import net.oneandone.sushi.util.Strings;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
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

    private Engine open() throws IOException {
        return Engine.open("/var/run/docker.sock", "target/wire.log");

    }
    @Test
    public void deviceFuse() throws IOException, InterruptedException {
        Engine engine;
        String image;
        String container;

        engine = open();
        image = engine.imageBuild("sometag", Collections.emptyMap(), df("FROM debian:stretch-slim\nCMD ls -la /dev/fuse\n"), null);
        container = engine.containerCreate(image, "somehost");
        engine.containerStart(container);
        Thread.sleep(1000);
        engine.containerRemove(container);
        engine.imageRemove(image);
    }

    @Test
    public void list() throws IOException {
        Map<String, String> labels;
        Engine engine;
        List<String> ids;
        String image;
        String container;

        labels = Strings.toMap("stooltest", UUID.randomUUID().toString());
        engine = open();
        assertTrue(engine.imageList(labels).isEmpty());
        engine.imageBuild("sometag", labels, df("FROM debian:stretch-slim\nRUN touch abc\nCMD echo hello\n"), null);
        ids = engine.imageList(labels);
        assertEquals(1, ids.size());
        image = ids.get(0);
        assertEquals(Arrays.asList(), engine.containerList(image));
        container = engine.containerCreate(image, "somehost");
        assertEquals(Arrays.asList(container), engine.containerList(image));
        engine.containerRemove(container);
        assertEquals(Arrays.asList(), engine.containerList(image));
        engine.imageRemove(image);
        assertEquals(Arrays.asList(), engine.imageList(labels));
    }

    @Test
    public void turnaround() throws IOException {
        final long limit = 1024*1024*5;
        String image;
        String message;
        Engine engine;
        String output;
        String container;
        Stats stats;

        message = UUID.randomUUID().toString();

        engine = open();
        image = engine.imageBuild("sometag",  Collections.emptyMap(), df("FROM debian:stretch-slim\nCMD echo " + message + ";sleep 5\n"),null);
        assertNotNull(image);

        container = engine.containerCreate(image, "foo", false,
                limit, null, null, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        assertNotNull(container);
        assertEquals(Engine.Status.CREATED, engine.containerStatus(container));
        assertNull(engine.containerStats(container));
        engine.containerStart(container);
        stats = engine.containerStats(container);
        assertEquals(0, stats.cpu);
        assertEquals(limit, stats.memoryLimit);
        assertTrue(stats.memoryUsage <= stats.memoryLimit);
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
        engine.containerRemove(container);
        try {
            assertNull(engine.containerStats(container));
            fail();
        } catch (FileNotFoundException e) {
            // ok
        }
        engine.imageRemove(image);
    }

    @Test
    public void stop() throws IOException {
        String image = "stooltest";
        Engine engine;
        String output;
        String container;
        long duration;

        engine = open();
        output = engine.imageBuildWithOutput(image, df("FROM debian:stretch-slim\nCMD [\"/bin/sleep\", \"30\"]\n"));
        assertNotNull(output);

        container = engine.containerCreate(image, "foo", false, null, /*"SIGQUIT"*/ null, 3, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        engine.containerStart(container);
        assertEquals(Engine.Status.RUNNING, engine.containerStatus(container));
        duration = System.currentTimeMillis();
        engine.containerStop(container, 6);
        duration = System.currentTimeMillis() - duration;
        assertTrue("about 6 seconds expected, took " + duration, duration >= 6000 && duration < 8000);
        assertEquals(Engine.Status.EXITED, engine.containerStatus(container));
        assertNotNull(engine.containerLogs(container));
        engine.containerRemove(container);
        engine.imageRemove(image);
    }

    @Test
    public void env() throws IOException, InterruptedException {
        String image = "stooltest";
        Engine engine;
        String output;
        String container;

        engine = open();
        output = engine.imageBuildWithOutput(image, df("FROM debian:stretch-slim\nCMD echo $foo $notfound $xxx\n"));
        assertNotNull(output);
        container = engine.containerCreate(image, "foo", false, null, /*"SIGQUIT"*/ null, 3,
                Strings.toMap("foo", "bar", "xxx", "after"), Collections.emptyMap(), Collections.emptyMap());
        engine.containerStart(container);
        assertEquals(Engine.Status.RUNNING, engine.containerStatus(container));
        Thread.sleep(1000);
        assertEquals(Engine.Status.EXITED, engine.containerStatus(container));
        assertEquals("bar after\n", engine.containerLogs(container));
        engine.containerRemove(container);
        engine.imageRemove(image);
    }

    @Test
    public void bindMount() throws IOException {
        FileNode home;
        FileNode file;
        String image = "stooltest";
        Engine engine;
        String output;
        String container;

        home = WORLD.getHome();
        file = home.createTempFile();
        engine = open();
        output = engine.imageBuildWithOutput(image, df("FROM debian:stretch-slim\nCMD ls " + file.getAbsolute() + "\n"));
        assertNotNull(output);

        container = engine.containerCreate(image, "foo", false, null, null, null, Collections.emptyMap(),
                Strings.toMap(home.getAbsolute(), home.getAbsolute()), Collections.emptyMap());
        assertNotNull(container);
        assertEquals(Engine.Status.CREATED, engine.containerStatus(container));
        engine.containerStart(container);
        assertEquals(0, engine.containerWait(container));
        output = engine.containerLogs(container);
        assertTrue(output.contains(file.getAbsolute()));
        engine.containerRemove(container);

        engine.imageRemove(image);
    }

    @Test
    public void runFailure() throws IOException {
        String image = "stooltest";
        Engine engine;

        engine = open();
        try {
            engine.imageBuildWithOutput(image, df("FROM debian:stretch-slim\nRUN /bin/nosuchcmd\nCMD [\"echo\", \"hi\", \"/\"]\n"));
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
        Engine engine;
        String container;

        engine = open();
        engine.imageBuildWithOutput(image, df("FROM debian:stretch-slim\nCMD [\"/nosuchcmd\"]\n"));
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

    @Test
    public void copy() throws IOException {
        String image = "stooltest";
        Engine engine;

        engine = open();
        engine.imageBuildWithOutput(image, WORLD.guessProjectHome(getClass()).join("src/test/docker"));
        engine.imageRemove(image);
    }

    @Test
    public void copyFailure() throws IOException {
        String image = "stooltest";
        Engine engine;

        engine = open();
        try {
            engine.imageBuildWithOutput(image, df("FROM debian:stretch-slim\ncopy nosuchfile /nosuchfile\nCMD [\"echo\", \"hi\", \"/\"]\n"));
            fail();
        } catch (BuildError e) {
            // ok
            assertTrue(e.error.contains("COPY failed"));
            assertNotNull("", e.output);
        }
    }

    //--

    private FileNode df(String dockerfile, FileNode ... extras) throws IOException {
        FileNode dir;

        dir = WORLD.getTemp().createTempDirectory();
        dir.join("Dockerfile").writeString(dockerfile);
        for (FileNode extra : extras) {
            extra.copyFile(dir.join(extra.getName()));
        }
        return dir;
    }
}
