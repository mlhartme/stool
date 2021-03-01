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
package net.oneandone.stool.cli.command;

import net.oneandone.inline.Console;
import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.cli.Context;
import net.oneandone.stool.core.Settings;
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
        Settings settings;
        String username;
        String password;
        Context context;

        settings = globals.settings();
        context = settings.currentContext();
        if (!context.hasToken()) {
            console.info.println("Nothing to do, there are no servers that need authentication.");
            return;
        }

        console.info.println(context.name + " " + context.url);
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
            context.auth(globals.getWorld(), settings.json, globals.caller(), username, password);
        } catch (StatusException e) {
            if (e.getStatusLine().code == 401) {
                throw new IOException(context.url + ": " + e.getMessage(), e);
            } else {
                throw e;
            }
        }
        settings.save(globals.configurationYaml());
        console.info.println("Successfully updated token for " + context.name);
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
