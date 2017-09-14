package net.oneandone.stool.docker;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.StatusException;
import net.oneandone.sushi.util.Strings;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EngineIT {
    private static final World WORLD = World.createMinimal();

    @Test
    public void turnaround() throws IOException {
        final int limit = 1024*1024*5;
        String image = "stooltest";
        String message;
        Engine engine;
        String output;
        String container;
        Stats stats;

        message = UUID.randomUUID().toString();

        engine = Engine.open("target/wire.log");
        output = engine.imageBuild(image, df("FROM debian:stretch-slim\nCMD echo " + message + ";sleep 5\n"));
        assertNotNull(output);

        container = engine.containerCreate(image, "foo", limit, Collections.emptyMap(), Collections.emptyMap());
        assertNotNull(container);
        assertEquals(Engine.Status.CREATED, engine.containerStatus(container));
        engine.containerStart(container);
        stats = engine.containerStats(container);
        assertEquals(0, stats.cpu);
        assertEquals(limit, stats.memoryLimit);
        assertTrue(stats.memoryUsage <= stats.memoryLimit);
        assertEquals(Engine.Status.RUNNING, engine.containerStatus(container));
        assertNotEquals(0, engine.containerStartedAt(container));
        assertEquals(0, engine.containerWait(container));
        assertEquals(Engine.Status.EXITED, engine.containerStatus(container));
        output = engine.containerLogs(container);
        assertTrue(output + " vs" + message, output.contains(message));
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
        file = home.join("foo").mkfile(); // home.createTempFile();
        engine = Engine.open("target/wire.log");
        output = engine.imageBuild(image, df("FROM debian:stretch-slim\nCMD ls " + file.getAbsolute() + "\n"));
        assertNotNull(output);

        container = engine.containerCreate(image, "foo", 0, Strings.toMap(home.getAbsolute(), home.getAbsolute()), Collections.emptyMap());
        assertNotNull(container);
        assertEquals(Engine.Status.CREATED, engine.containerStatus(container));
        engine.containerStart(container);
        assertEquals(0, engine.containerWait(container));
        output = engine.containerLogs(container);
        assertEquals(file.getAbsolute(), output.trim());
        engine.containerRemove(container);

        engine.imageRemove(image);
    }

    @Test
    public void runFailure() throws IOException {
        String image = "stooltest";
        Engine engine;

        engine = Engine.open("target/wire.log");
        try {
            engine.imageBuild(image, df("FROM debian:stretch-slim\nRUN /bin/nosuchcmd\nCMD [\"echo\", \"hi\", \"/\"]\n"));
            fail();
        } catch (BuildError e) {
            // ok
            assertNotNull("", e.error);
            assertEquals(127, e.code);
            assertNotNull("", e.output);
        }
    }

    @Test
    public void cmdFNotFound() throws IOException {
        String image = "stooltest";
        Engine engine;
        String container;

        engine = Engine.open("target/wire.log");
        engine.imageBuild(image, df("FROM debian:stretch-slim\nCMD [\"/nosuchcmd\"]\n"));
        container = engine.containerCreate(image, "foo");
        assertNotNull(container);
        assertEquals(Engine.Status.CREATED, engine.containerStatus(container));
        try {
            engine.containerStart(container);
            fail();
        } catch (StatusException e) {
            assertEquals(404, e.getStatusLine().code);
        }
    }

    @Test
    public void copy() throws IOException {
        String image = "stooltest";
        Engine engine;

        engine = Engine.open("target/wire.log");
        engine.imageBuild(image, WORLD.guessProjectHome(getClass()).join("src/test/docker"));
        engine.imageRemove(image);
    }

    @Test
    public void copyFailure() throws IOException {
        String image = "stooltest";
        Engine engine;

        engine = Engine.open("target/wire.log");
        try {
            engine.imageBuild(image, df("FROM debian:stretch-slim\ncopy nosuchfile /nosuchfile\nCMD [\"echo\", \"hi\", \"/\"]\n"));
            fail();
        } catch (BuildError e) {
            // ok
            assertNotNull("", e.error);
            assertEquals(-1, e.code);
            assertNotNull("", e.output);
        }
    }

    //--

    private FileNode df(String dockerfile) throws IOException {
        FileNode dir;

        dir = WORLD.getTemp().createTempDirectory();
        dir.join("Dockerfile").writeString(dockerfile);
        return dir;
    }
}
