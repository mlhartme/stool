/**
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
package net.oneandone.stool.locking;

import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.World;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class LockTest {
    @Test
    public void none() throws Exception {
        recursive(Mode.NONE, Mode.NONE);
    }

    @Test
    public void shared() throws Exception {
        recursive(Mode.SHARED, Mode.SHARED);
    }

    @Test
    public void exclusive() throws Exception {
        recursive(Mode.EXCLUSIVE, Mode.EXCLUSIVE);
    }

    private void recursive(Mode first, Mode second) throws Exception {
        LockManager manager;
        World world;
        Console console;

        world = new World();
        manager = LockManager.create(world.getTemp().createTempFile());
        console = Console.create(world);
        try (Lock f = manager.acquire("foo", console, first)) {
            try (Lock s = manager.acquire("foo", console, second)) {
                if (first == Mode.NONE && second == Mode.NONE) {
                    // skip
                } else {
                    assertFalse(manager.empty());
                }
            }
        }
        assertTrue(manager.empty());
    }
}
