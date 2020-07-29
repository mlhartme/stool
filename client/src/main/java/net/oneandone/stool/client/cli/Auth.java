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
import net.oneandone.stool.client.Context;
import net.oneandone.stool.client.Configuration;
import net.oneandone.sushi.fs.http.StatusException;

import java.io.IOException;

public class Auth {
    private final Globals globals;
    private final Console console;
    private final boolean batch;

    public Auth(Globals globals, boolean batch) {
        this.globals = globals;
        this.console = globals.getConsole();
        this.batch = batch;
    }

    public void run() throws Exception {
        Configuration configuration;
        String username;
        String password;

        configuration = globals.configuration();

        Context server = configuration.defaultContext();
        if (!server.hasToken()) {
            console.info.println("Nothing to do, there are no servers that need authentication.");
            return;
        }

        console.info.println(server.name + " " + server.url);
        if (batch) {
            username = env("STOOL_USERNAME");
            password = env("STOOL_PASSWORD");
            console.info.println("username: " + username);
            console.info.println("password: ********");
        } else {
            username = console.readline("username: ");
            password = new String(System.console().readPassword("password:"));
        }

        try {
            server.auth(globals.getWorld(), username, password);
        } catch (StatusException e) {
            if (e.getStatusLine().code == 401) {
                throw new IOException(server.url + ": " + e.getMessage(), e);
            } else {
                throw e;
            }
        }
        configuration.save(globals.getStoolYaml());
        console.info.println("Successfully updated token for " + server.name);
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
