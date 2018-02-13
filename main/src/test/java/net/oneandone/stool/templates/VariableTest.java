package net.oneandone.stool.templates;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class VariableTest {
    @Test
    public void normal() throws IOException {
        Variable v;

        assertNull(Variable.scan("# foo"));
        v = Variable.scan("#ENV Integer a 7");
        assertEquals("a", v.name);
        assertEquals(7, v.dflt);
        v = Variable.scan("#ENV Boolean b false");
        assertEquals("b", v.name);
        assertEquals(false, v.dflt);
        v = Variable.scan("#ENV Boolean b true");
        assertEquals("b", v.name);
        assertEquals(true, v.dflt);
    }
}
