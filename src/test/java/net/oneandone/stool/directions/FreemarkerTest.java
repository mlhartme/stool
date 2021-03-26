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
package net.oneandone.stool.directions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import freemarker.core.InvalidReferenceException;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.LocalSettings;
import net.oneandone.stool.core.Settings;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class FreemarkerTest {
    private static final World WORLD = World.createMinimal();

    private static Freemarker freemarker() {
        return freemarker(WORLD, "MOD", "a");
    }

    public static Freemarker freemarker(World world, String... env) {
        try {
            return new Freemarker(Strings.toMap(env), world.getTemp().createTempDirectory(), "stage", "localhost");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void normal() {
        Freemarker f;

        f = freemarker();
        assertEquals("", f.eval(""));
        assertEquals("hello", f.eval("hello"));
        assertEquals("stage.localhost", f.eval("${stool.fqdn}"));
    }

    @Test
    public void env() {
        assertEquals("a", freemarker().eval("${env.MOD}"));
    }

    @Test
    public void envNotFound() {
        try {
            freemarker().eval("${'1' + env.NOT_FOUND + 'x'}");
            fail();
        } catch (ArgumentException e) {
            assertEquals("env.NOT_FOUND", ((InvalidReferenceException) e.getCause()).getBlamedExpressionString());
        }
    }

    @Test
    public void envNotFoundDefault() throws IOException {
        assertEquals("x", freemarker().eval("${env.NOT_FOUND!'x'}"));
    }

    @Test
    public void directions() throws IOException {
        Freemarker e;
        Directions directions;
        Map<String, String> values;

        directions = Directions.forTest("name", "two", "'2' + direction.one", "one", "1");
        e = freemarker();
        values = e.eval(new HashMap<>(), directions.directions.values(), WORLD.getTemp().createTempDirectory());
        assertEquals(Strings.toMap("one", "1", "two", "21"), values);
    }

    @Test
    public void directionsRecursion() throws IOException {
        Directions directions;

        directions = Directions.forTest("name", "one", "'1' + direction.one");
        try {
            freemarker().eval(new HashMap<>(), directions.directions.values(), WORLD.getTemp().createTempDirectory());
            fail();
        } catch (ArgumentException e) {
            assertEquals("invalid recursion on direction one", e.getMessage());
        }
    }

    @Test
    public void script() throws IOException {
        Freemarker e;
        Directions directions;
        Map<String, String> values;
        FileNode dir;


        dir = WORLD.getTemp().createTempDirectory();
        dir.join("script.sh").writeString("""
            #!/bin/sh
            echo "arg:$1"
            """).setPermissions("rwxr-xr-x");
        directions = Directions.forTest("name", "one", "script.script('hello')");
        e = freemarker();
        values = e.eval(new HashMap<>(), directions.directions.values(), dir);
        assertEquals(Strings.toMap("one", "arg:hello\n"), values);
    }

    @Test
    public void swtchValue() throws JsonProcessingException {
        ObjectMapper yaml;
        JsonNode n;

        yaml = new ObjectMapper(new YAMLFactory());
        n = yaml.readTree("expr:\n  default: '0'\n  a: '1'\n  b: '2'");
        assertEquals("${ switch('MODE','0','a','1','b','2') }", Direction.getExpression((ObjectNode) n));
    }

    @Test
    public void swtch() {
        Freemarker e;

        e = freemarker();
        assertEquals("1", e.eval("${ switch('MOD', '0', 'a', '1', 'b', '2') }"));
    }
}