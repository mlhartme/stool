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
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Server;
import net.oneandone.stool.client.ServerManager;

public class Auth {
    private final Globals globals;
    private final Console console;
    private final String server;

    public Auth(Globals globals, String server) {
        this.globals = globals;
        this.console = globals.console;
        this.server = server;
    }


    public void run() throws Exception {
        ServerManager manager;
        String username;
        String password;
        Server dest;

        manager = globals.servers();
        dest = manager.get(server);
        username = console.readline("username: ");
        password = new String(System.console().readPassword("password:"));
        dest.auth(globals.world, username, password);
        manager.save();
        console.info.println("Successfully updated token for server " + dest.url);
    }
}
