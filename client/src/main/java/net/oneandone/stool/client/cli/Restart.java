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

import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Reference;

import java.util.ArrayList;
import java.util.List;

public class Restart extends StageCommand {
    private final List<String> selection;

    public Restart(Globals globals, List<String> selection) {
        super(globals);
        this.selection = selection;
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        if (up(reference)) {
            new Stop(globals, new ArrayList<>(selection(selection).keySet())).doRun(reference);
        } else {
            console.info.println("Container is not running - starting a new instance.");
        }
        new Start(globals, world, console, false, selection).doRun(reference);
    }
}
