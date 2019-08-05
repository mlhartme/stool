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
import net.oneandone.sushi.fs.http.StatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Auth {
    private final Globals globals;
    private final Console console;
    private final String explicitServer;

    public Auth(Globals globals, String server) {
        this.globals = globals;
        this.console = globals.getConsole();
        this.explicitServer = server;
    }


    public void run() throws Exception {
        ServerManager manager;
        String username;
        String password;
        List<Server> dests;

        manager = globals.servers();
        dests = new ArrayList<>();
        if (explicitServer != null) {
            dests.add(manager.get(explicitServer));
        } else {
            for (Server server : manager.enabledServer()) {
                if (server.hasToken()) {
                    dests.add(server);
                }
            }
            if (dests.isEmpty()) {
                console.info.println("Nothing to do, there are no servers that need authentication.");
                return;
            }
        }

        for (Server server : dests) {
            console.info.println(server.name + " " + server.url);
        }
        username = console.readline("username: ");
        password = new String(System.console().readPassword("password:"));
        for (Server dest : dests) {
            try {
                dest.auth(globals.getWorld(), username, password);
            } catch (StatusException e) {
                if (e.getStatusLine().code == 401) {
                    throw new IOException(dest.url + ": " + e.getMessage(), e);
                } else {
                    throw e;
                }
            }
        }
        manager.save(globals.getGson());
        console.info.println("Successfully updated token for " + dests.size() + " server(s)");
    }
}
