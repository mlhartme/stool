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
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class LockTest {
    private LockManager manager;
    private World world;
    private Console console;

    @Before
    public void before() throws Exception {
        world = new World();
        manager = LockManager.create(world.getTemp().createTempFile(), 2);
        console = Console.create(world);
    }

    //--

    @Test
    public void twoLocks() throws Exception {
        Lock first;
        Lock second;

        first = manager.acquire("foo", console, Mode.SHARED);
        second = manager.acquire("bar", console, Mode.EXCLUSIVE);
        first.close();
        second.close();
        assertTrue(manager.empty());
    }

    //-- recursive calls on same lock

    @Test
    public void noneNone() throws Exception {
        recursive(Mode.NONE, Mode.NONE);
    }

    @Test
    public void noneShared() throws Exception {
        recursive(Mode.NONE, Mode.SHARED);
    }

    @Test
    public void noneExclusive() throws Exception {
        recursive(Mode.NONE, Mode.EXCLUSIVE);
    }

    @Test
    public void sharedNone() throws Exception {
        recursive(Mode.SHARED, Mode.NONE);
    }

    @Test
    public void sharedShared() throws Exception {
        recursive(Mode.SHARED, Mode.SHARED);
    }

    @Test
    public void sharedExclusive() throws Exception {
        recursive(Mode.SHARED, Mode.EXCLUSIVE);
    }

    @Test
    public void exclusiveNone() throws Exception {
        recursive(Mode.EXCLUSIVE, Mode.NONE);
    }

    @Test
    public void exclusiveShared() throws Exception {
        recursive(Mode.EXCLUSIVE, Mode.SHARED);
    }

    @Test
    public void exclusiveExclsive() throws Exception {
        recursive(Mode.EXCLUSIVE, Mode.EXCLUSIVE);
    }

    private void recursive(Mode first, Mode second) throws Exception {
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
