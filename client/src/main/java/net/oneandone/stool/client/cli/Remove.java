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
import net.oneandone.stool.client.Project;
import net.oneandone.sushi.fs.World;

import java.io.IOException;

public class Remove extends StageCommand {
    private final boolean batch;
    private final boolean stop;

    public Remove(Globals globals, World world, Console console, boolean batch, boolean stop) {
        super(globals, world, console);
        this.batch = batch;
        this.stop = stop;
    }

    @Override
    public void doMain(Client client, String stage) throws Exception {
        boolean up;
        Project project;

        up = up(client, stage);
        if (stop && up) {
            new Stop(globals, world, console).doRun(client, stage);
            up = up(client, stage);
        }
        if (up) {
            throw new IOException("stage is not stopped.");
        }
        if (!batch) {
            console.info.println("Ready to delete stage " + stage + "?");
            console.pressReturn();
        }

        client.remove(stage);

        project = Project.lookup(world.getWorking());
        if (project != null && stage.equals(project.getAttachedOpt())) {
            console.info.println("removing backstage");
            project.removeBackstage();
        }
    }
}
