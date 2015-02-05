package net.oneandone.stool.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EnvironmentTest {
    private Environment env;

    public EnvironmentTest() {
        env = new Environment();
        env.set("A_B", "foo");
        env.set("x", "1");
    }

    @Test
    public void substitute() {
        subst("", "");
        subst("abc", "abc");
        subst("ab$c", "ab$$c");
        subst("1", "$x");
        subst("-1-", "-$x-");
        subst("1foo", "$x$A_B");
    }

    private void subst(String expected, String str) {
        assertEquals(expected, env.substitute(str));
    }
}
