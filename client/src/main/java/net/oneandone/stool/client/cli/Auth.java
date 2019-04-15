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

import java.net.InetAddress;
import java.net.UnknownHostException;

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
        String username;
        String password;

        username = console.readline("username: ");
        password = new String(System.console().readPassword("password:"));
        globals.servers().auth(server, username, password);
    }

    private String createdBy() {
        return System.getProperty("user.name");
    }

    private String createdOn() {
        try {
            return InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            return "unknown host: " + e.getMessage();
        }
    }
}
