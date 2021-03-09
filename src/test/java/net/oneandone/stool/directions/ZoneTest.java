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
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.oneandone.sushi.fs.World;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ZoneTest {
    private static final World WORLD = World.createMinimal();
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Test
    public void loadAll() throws IOException {
        Zone zone;
        Directions a;

        zone = Zone.load(WORLD, YAML, Library.fromDirectory(YAML, WORLD.guessProjectHome(getClass()).join("src/test/data/library").checkDirectory()));
        assertEquals(4, zone.size());
        a = zone.directions("derived");
        assertEquals("42", a.directions.get("asis").expression);
        assertEquals("modified", a.directions.get("base").expression);
        assertEquals("3", a.directions.get("added").expression);
    }
}
