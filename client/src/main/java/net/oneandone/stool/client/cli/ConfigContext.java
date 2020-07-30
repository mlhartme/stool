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
import net.oneandone.stool.client.AuthenticationException;
import net.oneandone.stool.client.Client;
import net.oneandone.stool.client.Configuration;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Context;

import java.io.IOException;

public class ConfigContext {
    private final Globals globals;
    private final Console console;
    private final boolean offline;
    private final String setOpt;

    public ConfigContext(Globals globals, boolean offline, String setOpt) {
        this.globals = globals;
        this.console = globals.getConsole();
        this.offline = offline;
        this.setOpt = setOpt;
    }

    public void run() throws Exception {
        Configuration configuration;
        Context old;
        String oldName;
        Context found;

        configuration = globals.configuration();
        if (setOpt == null) {
            console.info.println(configuration.currentContext().name);
        } else {
            found = configuration.contextLookup(setOpt);
            if (found == null) {
                throw new IOException(setOpt + ": context not found, available contexts: " + configuration.contexts.keySet());
            }
            old = configuration.currentContextOpt();
            oldName = old == null ? "(none)" : old.name;
            if (oldName.equals(setOpt)) {
                console.info.println("not changed: " + oldName);
            } else {
                configuration.setCurrentContext(setOpt);
                configuration.save(globals.getStoolYaml());
                console.info.println("changed " + oldName + " -> " + setOpt);
                if (!offline) {
                    check(found);
                }
            }
        }
    }

    private void check(Context context) throws Exception {
        Client client;

        client = context.connect(globals.getWorld());
        try {
            console.verbose.println("server info: " + client.info());
        } catch (AuthenticationException e) {
            console.verbose.println("authentication needed: " + e);
            e.printStackTrace(console.verbose);
            new Auth(globals, false).run();
            console.verbose.println("server info: " + context.connect(globals.getWorld()).info());
        }
    }
}
