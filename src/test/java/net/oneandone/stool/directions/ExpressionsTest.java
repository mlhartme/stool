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

public class ExpressionsTest {
    private static final World world = World.createMinimal();

    private static LocalSettings localSettings() { // TODO
        Settings c;
        try {
            c = Settings.create(world);
            c.local.environment.put("MOD", "a");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return c.local;
    }

    private static Expressions expressions() {
        return new Expressions(localSettings(), "stage");
    }

    @Test
    public void normal() throws IOException {
        Expressions e;

        e = expressions();
        assertEquals("", e.eval(""));
        assertEquals("hello", e.eval("hello"));
        assertEquals("stage.localhost", e.eval("${fqdn}"));
    }

    @Test
    public void env() throws IOException {
        assertEquals("a", expressions().eval("${env.MOD}"));
    }

    @Test
    public void envNotFound() {
        try {
            assertEquals("a", expressions().eval("${'1' + env.NOT_FOUND + 'x'}"));
            fail();
        } catch (IOException e) {
            assertEquals("env.NOT_FOUND", ((InvalidReferenceException) e.getCause()).getBlamedExpressionString());
        }
    }

    @Test
    public void directions() throws IOException {
        Expressions e;
        Directions directions;
        Map<String, String> values;

        directions = Directions.forTest("name", "one", "1", "two", "${'2' + value('one')}");
        e = expressions();
        values = e.eval(new HashMap<>(), directions, world.getTemp().createTempDirectory());
        assertEquals(Strings.toMap("one", "1", "two", "21"), values);
    }

    @Test
    public void exec() throws IOException {
        Expressions e;
        Directions directions;
        Map<String, String> values;
        FileNode dir;


        dir = world.getTemp().createTempDirectory();
        dir.join("script.sh").writeString("#!/bin/sh\necho \"arg:$1\"").setPermissions("rwxr-xr-x");
        directions = Directions.forTest("name", "one", "${exec('script.sh', 'hello')}");
        e = expressions();
        values = e.eval(new HashMap<>(), directions, dir);
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
    public void swtch() throws IOException {
        Expressions e;

        e = expressions();
        assertEquals("1", e.eval("${ switch('MOD', '0', 'a', '1', 'b', '2') }"));
    }
}