package net.oneandone.stool.docker;

import org.junit.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EngineIT {
    @Test
    public void turnaround() throws IOException {
        String message;
        Engine engine;
        String output;
        String id;

        message = UUID.randomUUID().toString();
        System.out.println("message " + message);

        engine = Engine.open("target/wire.log");
        output = engine.build("mhmtest", "FROM debian:stretch-slim\nCMD [\"echo\", \"" + message + "\", \"/\"]\n");
        System.out.println(output);
        assertNotNull(output);

        id = engine.containerCreate("mhmtest");
        assertNotNull(id);
        engine.containerStart(id);
        assertEquals("running", engine.containerStatus(id));
        assertEquals(0, engine.containerWait(id));
        assertEquals("exited", engine.containerStatus(id));
        output = engine.containerLogs(id);
        assertTrue(output + " vs" + message, output.contains(message));
        engine.containerRemove(id);

        engine.imageRemove("mhmtest");
    }
}
