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
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Session;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class Start extends ProjectCommand {
    private final boolean tail;
    private final boolean noCache;

    public Start(Session session, boolean tail, boolean noCache) {
        super(false, session, Mode.EXCLUSIVE, Mode.EXCLUSIVE, Mode.SHARED);
        this.tail = tail;
        this.noCache = noCache;
    }

    @Override
    public boolean doBefore(List<Project> projects, int indent) throws IOException {
        int global;
        int reserved;

        global = session.configuration.quota;
        if (global != 0) {
            reserved = session.quotaReserved();
            if (reserved > global) {
                throw new IOException("Sum of all stage quotas exceeds global limit: " + reserved + " mb > " + global + " mb.\n"
                  + "Use 'stool list name disk quota' to see actual disk usage vs configured quota.");
            }
        }
        return super.doBefore(projects, indent);
    }

    @Override
    public void doMain(Project project) throws Exception {
        project.stage.modify();
        // to avoid running into a ping timeout below:
        project.stage.session.configuration.verfiyHostname();
        project.checkConstraints();
        if (session.configuration.committed) {
            if (!project.isCommitted()) {
                throw new IOException("It's not allowed to start stages with local modifications.\n"
                        + "Please commit your modified files in order to start the stage.");
            }
        }
        checkNotStarted(project);

        doNormal(project);
    }

    @Override
    public void doFinish(Project project) throws Exception {
        // TODO - to avoid quick start/stop problems; just a ping doesn't solve this, and I don't understand why ...
        project.stage.awaitStartup(console);
        Thread.sleep(2000);
        console.info.println("Applications available:");
        for (String app : project.stage.namedUrls()) {
            console.info.println("  " + app);
        }
        if (tail) {
            doTail(project);
        }
    }

    //--

    public void doNormal(Project project) throws Exception {
        Ports ports;

        ports = session.pool().allocate(project, Collections.emptyMap());
        project.stage.start(console, ports, noCache);
    }

    private void doTail(Project project) throws IOException {
        console.info.println("Tailing container output.");
        console.info.println("Press Ctrl-C to abort.");
        console.info.println();
        project.stage.tailF(console.info);
    }

    private void checkNotStarted(Project project) throws IOException {
        if (project.getStage().state().equals(Project.State.UP)) {
            throw new IOException("Stage is already running.");
        }

    }
}
