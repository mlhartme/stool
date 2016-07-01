package net.oneandone.stool.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UrlTest {
    @Test
    public void normal() {
        check("http://foo.bar/");
        check("https://baz", "https://baz/");
        check("https://baz/path");
        check("https://baz/path/");
    }

    @Test
    public void protocols() {
        check("(http|https)://foo.bar/");
    }

    @Test
    public void hostnames() {
        check("http://(first|second)/");
    }

    @Test
    public void paths() {
        check("http://host/(p1|p2)");
    }

    @Test
    public void context() {
        check("http://host/context!");
        check("http://host/context!path");
        check("http://host/c1/c2!(p1|p2)");
    }

    private void check(String urlstr) {
        check(urlstr, urlstr);
    }

    private void check(String urlstr, String normalized) {
        Url url;

        url = Url.parse(urlstr);
        assertEquals(normalized, url.toString());
    }
}
