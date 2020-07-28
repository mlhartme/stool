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
import net.oneandone.stool.client.Configuration;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Server;

import java.io.IOException;

public class Context {
    private final Globals globals;
    private final Console console;
    private final String setOpt;

    public Context(Globals globals, String setOpt) {
        this.globals = globals;
        this.console = globals.getConsole();
        this.setOpt = setOpt;
    }

    public void run() throws IOException {
        Configuration configuration;
        String old;
        Server found;

        configuration = new Configuration(Setup.cisotoolsEnvironment(globals.getWorld()));
        configuration.load();
        if (setOpt == null) {
            console.info.println(configuration.server().name);
        } else {
            found = configuration.serverLookup(setOpt);
            if (found == null) {
                throw new IOException(setOpt + ": not found, available contexts: " + configuration.servers.keySet());
            }
            old = configuration.server().name;
            if (old.equals(setOpt)) {
                console.info.println("not changed: " + old);
            } else {
                configuration = configuration.withContext(setOpt);
                configuration.save(globals.getGson());
                console.info.println("changed " + old + " -> " + setOpt);
            }
        }
    }
}
