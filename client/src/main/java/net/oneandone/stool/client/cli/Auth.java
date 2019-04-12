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
import net.oneandone.stool.client.Client;
import net.oneandone.sushi.fs.World;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Auth {
    private final World world;
    private final Console console;

    public Auth(World world, Console console) {
        this.world = world;
        this.console = console;
    }


    public void run() throws Exception {
        String username;
        String password;
        Client client;

        username = console.readline("username: ");
        password = new String(System.console().readPassword("password:"));
        client = Client.basicAuth(world, null, "", "", username, password);
        world.getHome().join(".stool-token").writeString(client.auth());
        console.info.println("done");
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
