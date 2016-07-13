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
import net.oneandone.sushi.fs.DeleteException;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.NodeNotFoundException;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Cleanup extends StageCommand {
    public Cleanup(Session session) {
        super(false, true, session, Mode.NONE, Mode.EXCLUSIVE, Mode.EXCLUSIVE);
    }

    @Override
    public void doRun(Stage stage) throws Exception {
        stage.checkOwnership();
        cleanupMavenRepository(stage);
        rotateLogs(stage);
    }

    private void cleanupMavenRepository(Stage stage) throws NodeNotFoundException, DeleteException {
        FileNode repository;

        repository = stage.getBackstage().join(".m2");
        if (repository.exists()) {
            console.info.println("Removing Maven Repository at " + repository.getAbsolute());
            repository.deleteTree();
        } else {
            console.verbose.println("No Maven Repository found at " + repository.getAbsolute());
        }
    }

    private void rotateLogs(Stage stage) throws IOException {
        Node archived;

        for (Node logfile : stage.getBackstage().find("**/*.log")) {
            archived = archiveDirectory(logfile).join(logfile.getName() + ".gz");
            console.verbose.println(String.format("rotating %s to %s", logfile.getRelative(stage.getBackstage()),
                    archived.getRelative(stage.getBackstage())));
            logfile.gzip(archived);
            logfile.deleteFile();
        }
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private Node archiveDirectory(Node node) throws MkdirException {
        return node.getParent().join("archive", FMT.format(LocalDateTime.now())).mkdirsOpt();
    }
}
