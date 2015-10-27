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
package net.oneandone.stool.util;

import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.junit.Test;


public class LockTest {
    @Test
    public void normal() throws Exception {
        World world;
        Console console;
        FileNode file;

        world = new World();
        file = world.getTemp().createTempFile();
        console = Console.create(world);
        try (Lock first = Lock.create(file, console, Lock.Mode.NONE)) {
            System.out.println("first: " + first);
            try (Lock second = Lock.create(file, console, Lock.Mode.NONE)) {
                System.out.println("second: " + second);
            }
        }
    }
}
