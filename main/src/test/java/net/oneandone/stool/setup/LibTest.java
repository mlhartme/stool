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

import com.google.gson.Gson;
import net.oneandone.inline.Console;
import net.oneandone.stool.configuration.Expire;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.extensions.ExtensionsFactory;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LibTest {
    @Test
    public void create() throws Exception {
        World world;
        Console console;
        FileNode dir;
        Lib lib;
        String group;

        world = World.create();
        dir = world.getTemp().createTempDirectory();
        console = Console.create();
        group = dir.getGroup().toString();
        dir.deleteDirectory();
        lib = new Lib(console, dir, group, null);
        lib.create();
        assertNotNull(StoolConfiguration.load(Session.gson(world, ExtensionsFactory.create(world)), dir));
    }

    @Test
    public void upgrade() throws Exception {
        Gson gson;
        World world;
        Console console;
        FileNode libDir;
        Lib lib;
        String group;
        StoolConfiguration stool;
        StageConfiguration stage;

        world = World.create();
        console = Console.create();
        gson = Session.gson(world, ExtensionsFactory.create(world));
        libDir = world.getTemp().createTempDirectory();
        libDir.deleteDirectory();
        world.guessProjectHome(getClass()).join("src/test/upgrade").copy(libDir);
        group = libDir.getGroup().toString();
        lib = new Lib(console, libDir, group, null);
        lib.upgrade("3.3.4");
        stool = StoolConfiguration.load(gson, libDir);
        assertEquals("cpgem1.ciso.server.lan", stool.hostname);
        assertEquals("admin@email", stool.admin);
        stage = StageConfiguration.load(gson, libDir.join("backstages/stage/config.json"));
        assertEquals("151204.151204-6.2", stage.id);
        assertEquals(Strings.toList("prefix-[]", "[]-suffix"), stage.urls);
        assertEquals(Expire.never(), stage.expire);
        assertEquals("{\\+} \\u@\\h \\w \\$ ", stool.prompt);
        assertEquals(null, stool.defaults.get("https://svn.1and1.org/svn/controlpanel_app/controlpanel/").get("tomcat.perm"));
        assertEquals(Strings.toList(StageConfiguration.NOTIFY_OWNER), stage.notify);
    }
}
