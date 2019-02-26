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
import net.oneandone.stool.stage.Project;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Map;

public class Build extends ProjectCommand {
    private final boolean noCache;
    private final int keep;
    private final boolean restart;
    private final String comment;

    public Build(Session session, boolean noCache, int keep, boolean restart, String comment, FileNode project) {
        super(session, Mode.EXCLUSIVE, project);
        this.noCache = noCache;
        this.keep = keep;
        this.restart = restart;
        this.comment = comment;
    }

    @Override
    public void doRun(Project project) throws Exception {
        Stage stage;
        Ports ports;
        Map<String, FileNode> wars;

        wars = project.wars();
        if (wars.isEmpty()) {
            throw new IOException("no wars to build");
        }
        stage = session.load(session.projects().stage(project.getDirectory()));
        for (Map.Entry<String, FileNode> entry : wars.entrySet()) {
            ports = session.pool().allocate(stage, entry.getKey(), Collections.emptyMap());
            stage.build(entry.getKey(), entry.getValue(), console, ports, comment, project.getOrigin(), createdBy(), createdOn(), noCache, keep);
            if (restart) {
                new Restart(session, 0).doRun(stage);
            }
        }
    }

    private String createdBy() {
        return System.getProperty("user.name");
    }

    private String createdOn() {
        try {
            return InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            return "unknown host: " + e.getMessage();
        }
    }
}
