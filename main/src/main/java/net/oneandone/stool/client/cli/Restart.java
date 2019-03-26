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
import net.oneandone.stool.common.Reference;
import net.oneandone.stool.common.State;
import net.oneandone.stool.server.util.Server;
import net.oneandone.sushi.fs.World;

import java.util.ArrayList;
import java.util.List;

public class Restart extends StageCommand {
    private final List<String> selection;

    public Restart(World world, Console console, Server server, List<String> selection) {
        super(world, console, server);
        this.selection = selection;
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        State state;


        state = state(reference);
        if (state == State.UP || state == State.WORKING) {
            new Stop(world, console, server, new ArrayList<>(selection(selection).keySet())).doRun(reference);
        } else {
            console.info.println("Container is not running - starting a new instance.");
        }
        new Start(world, console, server, false, selection).doRun(reference);
    }
}
