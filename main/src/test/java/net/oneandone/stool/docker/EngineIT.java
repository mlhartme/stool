package net.oneandone.stool.docker;

import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EngineIT {
    @Test
    public void containers() throws IOException, URISyntaxException {
        Engine engine;
        String id;
        String output;

        engine = Engine.create();
        id = engine.containerCreate("hello-world");
        assertNotNull(id);
        engine.containerStart(id);
        assertEquals("running", engine.containerStatus(id));
        assertEquals(0, engine.containerWait(id));
        assertEquals("exited", engine.containerStatus(id));
        output = engine.containerLogs(id);
        assertTrue(output, output.contains("Hello from Docker"));
        engine.containerRemove(id);

    }
}
