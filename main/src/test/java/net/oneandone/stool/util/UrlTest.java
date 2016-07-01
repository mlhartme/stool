package net.oneandone.stool.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class UrlTest {
    @Test
    public void normal() {
        parse("http://foo.bar/");
        parse("https://baz", "https://baz/");
        parse("https://baz/path");
        parse("https://baz/path/");
    }

    @Test
    public void protocols() {
        parse("(http|https)://foo.bar/");
    }

    @Test
    public void hostnames() {
        parse("http://(first|second)/");
    }

    @Test
    public void paths() {
        parse("http://host/(p1|p2)");
    }

    @Test
    public void context() {
        parse("http://host/context!");
        parse("http://host/context!path");
        parse("http://host/c1/c2!(p1|p2)");
    }

    @Test
    public void subst() {
        Map<Character, String> map;
        Url url;

        map = new HashMap<>();
        map.put('p', "proto");
        map.put('h', "host");
        map.put('s', "segment");
        url = Url.parse("%p://h%h/con%stext");
        url = url.sustitute(map);
        assertEquals("proto://hhost/consegmenttext", url.toString());
    }

    @Test
    public void map() {
        map("http://host/path", "http://host/path");
        map("(http|https)://h", "http://h/", "https://h/");
    }

    public void map(String urlstr, String ... mapped) {
        Url url;

        url = Url.parse(urlstr);
        assertEquals(Arrays.asList(mapped), url.map());
    }

    private void parse(String urlstr) {
        parse(urlstr, urlstr);
    }

    private void parse(String urlstr, String normalized) {
        Url url;

        url = Url.parse(urlstr);
        assertEquals(normalized, url.toString());
    }
}
