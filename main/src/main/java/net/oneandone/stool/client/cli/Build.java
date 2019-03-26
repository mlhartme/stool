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

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.client.Project;
import net.oneandone.stool.common.Reference;
import net.oneandone.stool.server.util.Server;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Map;

public class Build extends ProjectCommand {
    private final boolean noCache;
    private final int keep;
    private final boolean restart;
    private final String comment;

    public Build(Server server, boolean noCache, int keep, boolean restart, String comment, FileNode project) {
        super(server, project);
        this.noCache = noCache;
        this.keep = keep;
        this.restart = restart;
        this.comment = comment;
    }

    @Override
    public void doRun(FileNode projectDirectory) throws Exception {
        Project project;
        Reference reference;
        Map<String, FileNode> wars;

        project = Project.lookup(projectDirectory);
        if (project == null) {
            throw new ArgumentException("unknown stage");
        }
        wars = project.wars();
        if (wars.isEmpty()) {
            throw new IOException("no wars to build");
        }
        reference = project.getAttachedOpt();
        if (reference == null) {
            throw new IOException("no stage attached to " + projectDirectory);
        }
        for (Map.Entry<String, FileNode> entry : wars.entrySet()) {
            console.info.println(entry.getKey() + ": building image for " + entry.getValue());
            server.build(reference, project, entry.getKey(), entry.getValue(), console, comment, project.getOrigin(), createdBy(), createdOn(), noCache, keep);
            if (restart) {
                new Restart(server, new ArrayList<>()).doRun(reference);
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
