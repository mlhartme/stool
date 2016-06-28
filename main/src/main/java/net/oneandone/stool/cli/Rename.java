/**
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
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.NodeAlreadyExistsException;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class Rename extends StageCommand {
    private final String name;

    public Rename(Session session, String name) {
        super(session, Mode.EXCLUSIVE, Mode.NONE, Mode.NONE);
        this.name = name;
    }

    @Override
    public void doRun(Stage stage) throws Exception {
        boolean selected;
        FileNode newBackstage;

        stage.checkOwnership();
        stage.checkNotUp();

        selected = session.isSelected(stage);
        stage.checkOwnership();
        Stage.checkName(name);
        newBackstage = session.backstage(name);
        try {
            newBackstage.checkNotExists();
        } catch (NodeAlreadyExistsException e) {
            throw new IOException("A stage with that name already exists. Please choose an other name.");
        }
        stage.backstageLink.move(newBackstage);
        stage.backstageLink = newBackstage;
        if (selected) {
            session.select(stage);
        }
    }
}
