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

import java.util.ArrayList;
import java.util.List;

public class Restart extends StageCommand {
    private final List<String> selection;

    public Restart(Globals globals, World world, Console console, List<String> selection) {
        super(globals, world, console);
        this.selection = selection;
    }

    @Override
    public void doMain(Client client, String stage) throws Exception {
        if (up(client, stage)) {
            new Stop(globals, world, console, new ArrayList<>(selection(selection).keySet())).doRun(client, stage);
        } else {
            console.info.println("Container is not running - starting a new instance.");
        }
        new Start(globals, world, console, false, selection).doRun(client, stage);
    }
}
