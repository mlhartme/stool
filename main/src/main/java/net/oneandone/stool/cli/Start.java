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

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class Start extends StageCommand {
    private final boolean tail;
    private final boolean noCache;

    public Start(Session session, boolean tail, boolean noCache) {
        super(false, session, Mode.EXCLUSIVE, Mode.EXCLUSIVE);
        this.tail = tail;
        this.noCache = noCache;
    }

    @Override
    public boolean doBefore(List<Stage> stages, int indent) throws IOException {
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
        return super.doBefore(stages, indent);
    }

    @Override
    public void doMain(Stage stage) throws Exception {
        stage.modify();
        // to avoid running into a ping timeout below:
        stage.session.configuration.verfiyHostname();
        stage.checkConstraints();
        checkNotStarted(stage);

        doNormal(stage);
    }

    @Override
    public void doFinish(Stage stage) throws Exception {
        // TODO - to avoid quick start/stop problems; just a ping doesn't solve this, and I don't understand why ...
        stage.awaitStartup(console);
        Thread.sleep(2000);
        console.info.println("Applications available:");
        for (String app : stage.namedUrls()) {
            console.info.println("  " + app);
        }
        if (tail) {
            doTail(stage);
        }
    }

    //--

    public void doNormal(Stage stage) throws Exception {
        Ports ports;
        Project project;

        project = Project.load(session, session.backstageLink(stage.getId()));
        ports = session.pool().allocate(stage, project.selectedWars(), Collections.emptyMap());
        stage.start(project.wars(), console, ports, noCache);
    }

    private void doTail(Stage stage) throws IOException {
        console.info.println("Tailing container output.");
        console.info.println("Press Ctrl-C to abort.");
        console.info.println();
        stage.tailF(console.info);
    }

    private void checkNotStarted(Stage stage) throws IOException {
        if (stage.state().equals(Stage.State.UP)) {
            throw new IOException("Stage is already running.");
        }

    }
}
