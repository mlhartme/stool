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

import net.oneandone.stool.cli.AuthenticationException;
import net.oneandone.stool.cli.Client;
import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.cli.Context;
import net.oneandone.stool.core.Settings;

import java.io.IOException;

public class ConfigContext extends ClientCommand {
    private final boolean offline;
    private final boolean quiet;
    private final String setOpt;

    public ConfigContext(Globals globals, boolean offline, boolean quiet, String setOpt) {
        super(globals);
        this.offline = offline;
        this.quiet = quiet;
        this.setOpt = setOpt;
    }

    public void run() throws Exception {
        Settings settings;
        Context old;
        String oldName;
        Context found;
        String current;

        settings = globals.settings();
        if (setOpt == null) {
            if (quiet) {
                console.info.println(settings.currentContext().name);
            } else {
                found = settings.currentContextOptWarn(console.info);
                current = found == null ? null : found.name;
                for (Context context : settings.contexts().values()) {
                    console.info.print(context.name.equals(current) ? "=> " : "   ");
                    console.info.print(context.name);
                    console.info.println(context.isKube() ? " (Kubernetes)" : "");
                }
            }
        } else {
            found = settings.contextLookup(setOpt);
            if (found == null) {
                throw new IOException(setOpt + ": context not found, available context: " + settings.contexts().keySet());
            }
            old = settings.currentContextOptWarn(console.info);
            oldName = old == null ? "(none)" : old.name;
            if (oldName.equals(setOpt)) {
                console.info.println("not changed: " + oldName);
            } else {
                settings.setCurrentContext(setOpt);
                settings.save(globals.settingsYaml());
                console.info.println("changed " + oldName + " -> " + setOpt);
                if (!offline) {
                    check(found);
                }
            }
        }
    }

    private void check(Context context) throws Exception {
        Client client;

        client = context.connect(globals.settings().local, globals.caller());
        try {
            // check if we need authentication; CAUTION: don't use version because it doesn't need credentials
            client.list("arbitraryStageNameFilter");
        } catch (AuthenticationException e) {
            console.info.println("authentication required");
            console.verbose.println(e);
            e.printStackTrace(console.verbose);
            new Auth(globals, false).run();
        }
        console.verbose.println("server info: " + globals.settings().currentContext().connect(globals.settings().local, globals.caller()).version());
    }
}
