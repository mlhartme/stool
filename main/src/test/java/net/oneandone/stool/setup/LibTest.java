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
package net.oneandone.stool.setup;

import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.extensions.ExtensionsFactory;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LibTest {
    @Test
    public void create() throws Exception {
        Console console;
        FileNode dir;
        Lib lib;
        String group;

        console = Console.create(new World());
        dir = console.world.getTemp().createTempDirectory();
        group = dir.getGroup().toString();
        dir.deleteDirectory();
        lib = new Lib(console, dir, group, null);
        lib.create();
        assertNotNull(StoolConfiguration.load(Session.gson(console.world, ExtensionsFactory.create(console.world)), dir));
    }

    @Test
    public void upgrade() throws Exception {
        Console console;
        FileNode dir;
        Lib lib;
        String group;
        StoolConfiguration stool;

        console = Console.create(new World());
        dir = console.world.getTemp().createTempDirectory();
        dir.deleteDirectory();
        console.world.guessProjectHome(getClass()).join("src/test/upgrade").copy(dir);
        group = dir.getGroup().toString();
        lib = new Lib(console, dir, group, null);
        lib.upgrade("3.3.4");
        stool = StoolConfiguration.load(Session.gson(console.world, ExtensionsFactory.create(console.world)), dir);
        assertEquals("cpgem1.ciso.server.lan", stool.hostname);
        assertEquals("admin@email", stool.admin);
    }
}
