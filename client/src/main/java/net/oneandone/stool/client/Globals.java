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
package net.oneandone.stool.client;

import net.oneandone.inline.Console;
import net.oneandone.stool.client.Client;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

/** Basically a session factory */
public class Globals {
    public final Console console;
    public final World world;
    private final FileNode clientHome;
    private final String clientInvocation;
    private final String clientCommand;
    private FileNode wirelog;

    public Globals(Console console, World world, FileNode clientHome, String clientInvocation, String clientCommand) {
        this.console = console;
        this.world = world;
        this.clientHome = clientHome;
        this.clientInvocation = clientInvocation;
        this.clientCommand = clientCommand;
        this.wirelog = null;
    }

    public void setWirelog(String wirelog) {
        this.wirelog = wirelog == null ? null : world.file(wirelog);
    }

    public void setException(boolean exception) {
        if (exception) {
            throw new RuntimeException("intentional exception");
        }
    }

    public World world() {
        return world;
    }

    public Console console() {
        return console;
    }

    public Servers servers() throws IOException {
        FileNode file;
        Servers result;

        file = clientHome.join(".stool-servers");
        result = new Servers(file, wirelog, clientInvocation, clientCommand);
        if (file.exists()) {
            result.load();
        } else {
            result.defaultServers();
            result.save();
        }
        return result;
    }
}
