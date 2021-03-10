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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.oneandone.sushi.fs.World;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class DirectionsTest {
    private static final World WORLD = World.createMinimal();
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Test
    public void stage() throws IOException {
        Directions c;

        c = Directions.loadStageDirectionsBase(WORLD, YAML, new Library("empty", WORLD.getTemp().createTempDirectory()));
        assertEquals("stage", c.subject);
    }

    @Test
    public void empty() throws IOException {
        Directions c;

        c = create("""
                 DIRECTIONS: 'foo'
                 EXTENDS: 'base'
                 """);
        assertEquals("foo", c.subject);
        assertEquals(1, c.size());
    }

    @Test
    public void override() throws IOException {
        Directions c;

        c = create("""
                DIRECTIONS: 'foo'
                EXTENDS: 'base'
                f:
                  expr: 2
                """);
        assertEquals("foo", c.subject);
        assertEquals(1, c.size());
        System.out.println(c.toObject(YAML));
    }

    // TODO @Test
    public void extraValueOverrides() throws IOException {
        try {
            create("""
                    DIRECTIONS: 'foo'
                    EXTENDS: 'base'
                    f:
                      expr: 2
                      extra: true
                    """);
            fail();
        } catch (IllegalStateException e) {
            assertEquals("extra direction overrides base direction: f", e.getMessage());
        }
    }

    @Test
    public void extra() throws IOException {
        Directions c;

        c = create("""
                DIRECTIONS: 'foo'
                EXTENDS: 'base'
                v:
                    expr: 2
                    extra: true
                """);
        assertEquals("foo", c.subject);
        assertEquals(2, c.size());
    }

    // TODO @Test
    public void extraValueExpected() throws IOException {
        try {
            create("""
                DIRECTIONS: 'foo'
                EXTENDS: 'base'
                v: 2
                """);
            fail();
        } catch (IllegalStateException e) {
            assertEquals("extra direction expected: v", e.getMessage());
        }
    }

    @Test
    public void ext() throws IOException {
        Library library;

        library = library("""
                DIRECTIONS: 'first'
                v: 1
                """,
                """
                DIRECTIONS: "second"
                EXTENDS: "first"
                v: 2
                """);
        assertEquals("1", library.directions("first").get("v").expression);
        assertEquals("2", library.directions("second").get("v").expression);
    }

    private Directions create(String str) throws IOException {
        Library library;
        ObjectNode obj;

        library = new Library("empty", WORLD.getTemp().createTempDirectory());
        library.addDirections(Directions.forTest("base", "f", "1"));
        obj = (ObjectNode) YAML.readTree(str);
        return Directions.loadLiteral(library, "", null, obj);
    }

    private Library library(String ... directionsArray) throws IOException {
        Library library;
        ObjectNode obj;

        library = new Library("empty", WORLD.getTemp().createTempDirectory());
        library.addDirections(Directions.forTest("base", "f", "1"));
        for (String directions : directionsArray) {
            obj = (ObjectNode) YAML.readTree(directions);
            library.addDirections(Directions.loadLiteral(library, "", null, obj));
        }
        return library;
    }
}
