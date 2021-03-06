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

import net.oneandone.stool.cli.Client;
import net.oneandone.stool.cli.Globals;

public class Describe extends ClientCommand {
    private final String ref;

    public Describe(Globals globals, String ref) {
        super(globals);
        this.ref = ref;
    }

    public void run() throws Exception {
        Client client;

        client = globals.settings().currentContext().connect(globals.settings().local, globals.caller());
        for (String line : client.describe(ref)) {
            console.info.println(line);
        }
    }
}
