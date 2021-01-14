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
package net.oneandone.stool.values;

import net.oneandone.sushi.fs.World;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HelmClassTest {
    @Test
    public void normal() throws IOException {
        World world;
        Map<String, HelmClass> all;
        HelmClass a;

        world = World.create();
        all = HelmClass.loadAll(world.guessProjectHome(getClass()).join("src/test/classes").checkDirectory());
        assertEquals(2, all.size());
        a = all.get("derived");
        assertEquals("42", a.values.get("asis").macro);
        assertEquals("modified", a.values.get("base").macro);
        assertEquals("3", a.values.get("added").macro);
    }
}
