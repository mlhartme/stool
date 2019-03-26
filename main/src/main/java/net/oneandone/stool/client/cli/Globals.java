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
package net.oneandone.stool.client.cli;

import net.oneandone.inline.Console;
import net.oneandone.stool.server.util.Server;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

/** Basically a session factory */
public class Globals {
    private final FileNode itHome;
    private final String[] args;
    public final Console console;
    public final World world;

    public Globals(FileNode itHome, String[] args, Console console, World world) {
        this.itHome = itHome;
        this.args = args;
        this.console = console;
        this.world = world;
    }

    public void setException(boolean exception) {
        if (exception) {
            throw new RuntimeException("intentional exception");
        }
    }

    public Server server() throws IOException {
        return net.oneandone.stool.server.cli.Globals.create(world, itHome, args).server();
    }
}
