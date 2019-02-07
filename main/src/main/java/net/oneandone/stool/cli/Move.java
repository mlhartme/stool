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

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Project;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;

public class Move extends StageCommand {
    private FileNode dest;

    public Move(Session session, FileNode dest) {
        super(true, session, Mode.EXCLUSIVE, Mode.EXCLUSIVE, Mode.EXCLUSIVE);
        this.dest = dest;
    }

    @Override
    public void doMain(Project project) throws Exception {
        project.stage.modify();
        project.getStage().checkNotUp();

        if (dest.exists()) {
            dest.checkDirectory();
            dest = dest.join(project.getDirectory().getName());
            dest.checkNotExists();
        } else {
            dest.getParent().checkDirectory();
        }
        if (dest.hasAncestor(project.getDirectory())) {
            throw new ArgumentException("you cannot move a stage into a subdirectory of itself");
        }

        project.move(dest);
        console.info.println("done");
    }
}
