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
package net.oneandone.stool;

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Lock;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Value;
import net.oneandone.sushi.fs.file.FileNode;

public class Move extends StageCommand {
    @Value(name = "dest", position = 1)
    private FileNode dest;

    public Move(Session session) {
        super(session);
    }

    @Override
    public Lock lock() {
        return session.lock();
    }

    @Override
    public Lock stageLock(Stage stage) {
        return null;
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        stage.checkOwnership();
        stage.checkStopped();

        if (dest.exists()) {
            dest.checkDirectory();
            dest = dest.join(stage.getDirectory().getName());
            dest.checkNotExists();
        } else {
            dest.getParent().checkDirectory();
        }
        if (dest.hasAnchestor(stage.getDirectory())) {
            throw new ArgumentException("you cannot move a stage into a subdirectory of itself");
        }

        stage.move(dest);
        console.info.println("done");
        if (session.isSelected(stage)) {
            session.select(stage);
        }
    }
}
