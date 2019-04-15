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

import net.oneandone.stool.client.Project;
import net.oneandone.stool.client.Reference;

import java.io.IOException;

public class Remove extends StageCommand {
    private final boolean batch;
    private final boolean stop;

    public Remove(Globals globals, boolean batch, boolean stop) {
        super(globals);
        this.batch = batch;
        this.stop = stop;
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        boolean up;
        Project project;

        up = up(reference);
        if (stop && up) {
            new Stop(globals).doRun(reference);
            up = up(reference);
        }
        if (up) {
            throw new IOException("stage is not stopped.");
        }
        if (!batch) {
            console.info.println("Ready to delete stage " + reference.stage + "?");
            console.pressReturn();
        }

        reference.client.remove(reference.stage);

        project = Project.lookup(world.getWorking());
        if (project != null && reference.stage.equals(project.getAttachedOpt())) {
            console.info.println("removing backstage");
            project.removeBackstage();
        }
    }
}
