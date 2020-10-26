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
package net.oneandone.stool.stockimage;

import net.oneandone.inline.Console;
import net.oneandone.sushi.fs.DirectoryNotFoundException;
import net.oneandone.sushi.fs.ExistsException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

/** Global client stuff */
public class Globals {
    private final Console console;
    private final World world;

    public Globals(Console console, World world) {
        this.console = console;
        this.world = world;
    }

    public FileNode templates() throws ExistsException, DirectoryNotFoundException {
        return world.file(System.getenv("CISOTOOLS_HOME")).join("stool/templates-6").checkDirectory(); // TODO
    }

    public World getWorld() {
        return world;
    }

    public Console getConsole() {
        return console;
    }
}
