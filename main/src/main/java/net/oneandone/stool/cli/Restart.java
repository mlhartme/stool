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
package net.oneandone.stool.cli;

import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Reference;
import net.oneandone.stool.stage.State;
import net.oneandone.stool.util.Server;
import net.oneandone.stool.util.Session;

import java.util.ArrayList;
import java.util.List;

public class Restart extends StageCommand {
    private final List<String> selection;

    public Restart(Server server, List<String> selection) {
        super(server, /* locking done by subcommands */ Mode.NONE, Mode.NONE);
        this.selection = selection;
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        State state;


        state = server.state(reference);
        if (state == State.UP || state == State.WORKING) {
            new Stop(server, new ArrayList<>(selection(selection).keySet())).doRun(reference);
        } else {
            console.info.println("Container is not running - starting a new instance.");
        }
        new Start(server, false, selection).doRun(reference);
    }
}
