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

import net.oneandone.stool.Main;
import net.oneandone.stool.cli.Client;
import net.oneandone.stool.cli.Context;
import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.core.Configuration;

import java.io.IOException;

public class Version extends ClientCommand {
    public Version(Globals globals) {
        super(globals);
    }

    public void run() throws IOException {
        Configuration configuration;
        Context context;
        Client client;

        console.info.println("client version: " + Main.versionString(world));
        configuration = globals.configuration();
        context = configuration.currentContextOpt();
        if (context != null) {
            client = context.connect(world, globals.configuration(), globals.caller());
            console.info.println("server " + context.url + " version: " + client.version());
        }
    }
}
