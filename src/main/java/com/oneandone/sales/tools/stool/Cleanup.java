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
package com.oneandone.sales.tools.stool;

import com.oneandone.sales.tools.stool.stage.Stage;
import com.oneandone.sales.tools.stool.util.Session;
import net.oneandone.sushi.fs.DeleteException;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.NodeNotFoundException;
import net.oneandone.sushi.fs.file.FileNode;
import org.joda.time.DateTime;

import java.io.IOException;
public class Cleanup extends StageCommand {

    public Cleanup(Session session) {
        super(session);
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        stage.checkOwnership();
        cleanupMavenReposity(stage);
        rotateLogs(stage);
    }

    public void cleanupMavenReposity(Stage stage) throws NodeNotFoundException, DeleteException {
        FileNode repository;
        repository = stage.getWrapper().join(".m2");
        if (repository.exists()) {
            console.info.println("Removing Maven Repository at " + repository.getAbsolute());
            repository.deleteTree();
        } else {
            console.info.println("No Maven Repository found at " + repository.getAbsolute());
        }
    }

    public void rotateLogs(Stage stage) throws IOException {
        Node archivedLog;
        for (Node logfile : stage.getWrapper().find("**/*.log")) {
            archivedLog = archiveDirectory(logfile).join(logfile.getName() + ".gz");
            console.verbose.println(String.format("rotating %s to %s", logfile.getRelative(stage.getWrapper()),
              archivedLog.getRelative(stage.getWrapper())));
            logfile.gzip(archivedLog);
            logfile.deleteFile();
        }
    }

    public Node archiveDirectory(Node node) throws MkdirException {
        return node.getParent().join("archive", new DateTime().toString("yyyy-MM-dd_HH-mm-ss")).mkdirsOpt();
    }
}
