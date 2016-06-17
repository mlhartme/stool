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

import net.oneandone.inline.Console;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Locking tests with two different LockMangers - should be similar to test with two different VMs */
public class Lock2Test {
    private LockManager managerA;
    private LockManager managerB;
    private World world;
    private Console console;

    @Before
    public void before() throws Exception {
        FileNode tmp;

        world = World.create();
        tmp = world.getTemp().createTempFile();
        managerA = LockManager.create(tmp, "a", 2);
        managerB = new LockManager(tmp, new Process(0, ""), 2);
        console = Console.create();
    }

    @After
    public void after() throws Exception {
        assertTrue(managerA.empty());
        assertTrue(managerB.empty());
        managerA.close();
        managerB.close();
    }

    @Test
    public void sharedShared() throws Exception {
        Lock first;
        Lock second;

        first = managerA.acquire("foo", console, Mode.SHARED);
        second = managerB.acquire("foo", console, Mode.SHARED);
        first.close();
        second.close();
    }

    @Test
    public void sharedExclusive() throws Exception {
        Lock first;
        Lock second;

        first = managerA.acquire("foo", console, Mode.SHARED);
        assertNull(managerB.acquireOpt("foo", console, Mode.EXCLUSIVE));
        assertNull(managerB.acquireOpt("foo", console, Mode.EXCLUSIVE));
        first.close();
        second = managerB.acquire("foo", console, Mode.EXCLUSIVE);
        second.close();
    }

    @Test
    public void sharedExclusiveExclusive() throws Exception {
        Lock first;
        Lock second;

        first = managerA.acquire("foo", console, Mode.EXCLUSIVE);
        assertNull(managerB.acquireOpt("foo", console, Mode.EXCLUSIVE));
        assertNull(managerB.acquireOpt("foo", console, Mode.EXCLUSIVE));
        first.close();
        second = managerB.acquire("foo", console, Mode.EXCLUSIVE);
        second.close();
    }
}
