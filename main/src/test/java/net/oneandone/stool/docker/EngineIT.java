package net.oneandone.stool.docker;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.junit.Test;

import java.io.IOException;
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
        String image = "stooltest";
        String message;
        Engine engine;
        String output;
        String container;

        message = UUID.randomUUID().toString();

        engine = Engine.open("target/wire.log");
        output = engine.imageBuild(image, df("FROM debian:stretch-slim\nCMD [\"echo\", \"" + message + "\", \"/\"]\n"));
        System.out.println(output);
        assertNotNull(output);

        container = engine.containerCreate(image, "foo");
        assertNotNull(container);
        assertEquals(Engine.Status.CREATED, engine.containerStatus(container));
        engine.containerStart(container);
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
