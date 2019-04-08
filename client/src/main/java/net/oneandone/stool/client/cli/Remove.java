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
import net.oneandone.stool.client.Project;
import net.oneandone.stool.client.Reference;
import net.oneandone.stool.client.State;
import net.oneandone.stool.client.Client;
import net.oneandone.sushi.fs.World;

import java.io.IOException;

public class Remove extends StageCommand {
    private final boolean batch;
    private final boolean stop;

    public Remove(World world, Console console, Client client, boolean batch, boolean stop) {
        super(world, console, client);
        this.batch = batch;
        this.stop = stop;
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        State state;
        Project project;

        state = state(reference);
        if (stop && state == State.UP) {
            new Stop(world, console, (Client) client).doRun(reference);
            state = state(reference);
        }
        if (state == State.UP) {
            throw new IOException("stage is not stopped.");
        }
        if (!batch) {
            console.info.println("Ready to delete stage " + reference.getName() + "?");
            console.pressReturn();
        }

        client.remove(reference);

        project = Project.lookup(world.getWorking());
        if (project != null && reference.equals(project.getAttachedOpt())) {
            console.info.println("removing backstage");
            project.removeBackstage();
        }
    }
}
