/*
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
