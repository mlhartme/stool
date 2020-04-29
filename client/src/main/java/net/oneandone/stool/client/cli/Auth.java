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
import net.oneandone.stool.client.Configuration;
import net.oneandone.sushi.fs.http.StatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Auth {
    private final Globals globals;
    private final Console console;
    private final boolean batch;
    private final String explicitServer;

    public Auth(Globals globals, boolean batch, String server) {
        this.globals = globals;
        this.console = globals.getConsole();
        this.batch = batch;
        this.explicitServer = server;
    }


    public void run() throws Exception {
        Configuration manager;
        String username;
        String password;
        List<Server> dests;

        manager = globals.configuration();
        dests = new ArrayList<>();
        if (explicitServer != null) {
            dests.add(manager.serverGet(explicitServer));
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
        if (batch) {
            username = env("STOOL_USERNAME");
            password = env("STOOL_PASSWORD");
            console.info.println("username: " + username);
            console.info.println("password: ********");
        } else {
            username = console.readline("username: ");
            password = new String(System.console().readPassword("password:"));
        }
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

    private static String env(String name) throws IOException {
        String result;

        result = System.getenv(name);
        if (result == null) {
            throw new IOException("environment variable not found: " + name);
        }
        return result;
    }
}
