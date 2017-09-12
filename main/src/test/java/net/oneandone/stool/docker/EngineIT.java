package net.oneandone.stool.docker;

import org.junit.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EngineIT {
    @Test
    public void turnaround() throws IOException {
        String image = "stooltest";
        String message;
        Engine engine;
        String output;
        String container;

        message = UUID.randomUUID().toString();
        System.out.println("message " + message);

        engine = Engine.open("target/wire.log");
        output = engine.build(image, "FROM debian:stretch-slim\nCMD [\"echo\", \"" + message + "\", \"/\"]\n");
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
}
