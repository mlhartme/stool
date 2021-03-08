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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class DirectionsTest {
    private static final World WORLD = World.createMinimal();
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Test
    public void stage() throws IOException {
        Directions c;

        c = Directions.loadStageDirectionsBase(WORLD, YAML);
        assertEquals("stage", c.name);
    }

    @Test
    public void empty() throws IOException {
        Directions c;

        c = create("NAME: 'foo'\nEXTENDS: 'base'\n");
        assertEquals("foo", c.name);
        assertEquals(1, c.size());
    }

    @Test
    public void override() throws IOException {
        Directions c;

        c = create("NAME: 'foo'\nEXTENDS: 'base'\nf:\n  expr: 2\n");
        assertEquals("foo", c.name);
        assertEquals(1, c.size());
        System.out.println(c.toObject(YAML));
    }

    @Test
    public void extraValueOverrides() throws IOException {
        try {
            create("NAME: 'foo'\nEXTENDS: 'base'\nf:\n    expr: 2\n    extra: true");
            fail();
        } catch (IllegalStateException e) {
            assertEquals("extra direction overrides base direction: f", e.getMessage());
        }
    }

    @Test
    public void extra() throws IOException {
        Directions c;

        c = create("NAME: 'foo'\nEXTENDS: 'base'\nv:\n    expr: 2\n    extra: true");
        assertEquals("foo", c.name);
        assertEquals(2, c.size());
    }

    @Test
    public void extraValueExpected() throws IOException {
        try {
            create("NAME: 'foo'\nEXTENDS: 'base'\nv: 2\n");
            fail();
        } catch (IllegalStateException e) {
            assertEquals("extra direction expected: v", e.getMessage());
        }
    }

    @Test
    public Directions create(String str) throws IOException {
        Map<String, Directions> all;
        ObjectNode obj;

        obj = (ObjectNode) YAML.readTree(str);
        all = new HashMap<>();
        all.put("base", Directions.forTest("base", "f", "1"));
        return Directions.loadLiteral(all, "", null, obj);
    }

    //--

    @Test
    public void loadAll() throws IOException {
        Map<String, Directions> all;
        Directions a;

        all = DirectionsRef.loadAll(WORLD, YAML, Collections.singleton(WORLD.guessProjectHome(getClass()).join("src/test/directions/foo").checkDirectory()));
        assertEquals(4, all.size());
        a = all.get("derived");
        assertEquals("42", a.directions.get("asis").expression);
        assertEquals("modified", a.directions.get("base").expression);
        assertEquals("3", a.directions.get("added").expression);
    }
}
