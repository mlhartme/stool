package net.oneandone.stool.util;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class MultiStringTest {
    @Test
    public void append() {
        check("http://host/path", "http://host/path");
        check("(http|https)://h", "http://h", "https://h");
        check("(abc)1", "abc1");
        check("()1");
        check("(a|b|c)(1|2)", "a1", "b1", "c1", "a2", "b2", "c2");
    }

    public void check(String str, String ... mapped) {
        MultiString ms;

        ms = new MultiString();
        ms.append(str);
        assertEquals(Arrays.asList(mapped), ms.lst);
    }
}
