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
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

public class Start extends StageCommand {
    private boolean debug;
    private boolean suspend;
    private boolean tail;

    public Start(Session session, boolean debug, boolean suspend, boolean tail) {
        super(false, session, Mode.EXCLUSIVE, Mode.EXCLUSIVE, Mode.SHARED);
        this.debug = debug;
        this.suspend = suspend;
        this.tail = tail;
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
        if (session.configuration.committed) {
            if (!stage.isCommitted()) {
                throw new IOException("It's not allowed to start stages with local modifications.\n"
                        + "Please commit your modified files in order to start the stage.");
            }
        }
        checkNotStarted(stage);

        doNormal(stage);
        if (session.bedroom.contains(stage.getId())) {
            console.info.println("leaving sleeping state");
            session.bedroom.remove(session.gson, stage.getId());
        }
    }

    @Override
    public void doFinish(Stage stage) throws Exception {
        ping(stage);
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

        ports = session.pool().allocate(stage, Collections.emptyMap());
        createBackstage(stage);
        if (debug || suspend) {
            console.info.println("debugging enabled on port " + ports.debug());
        }
        stage.start(console, ports);
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

    private void ping(Stage stage) throws IOException, URISyntaxException, InterruptedException {
        URI uri;
        int count;

        console.info.println("Ping'n Applications.");
        for (String url : stage.urlMap().values()) {
            if (url.startsWith("http://")) {
                uri = new URI(url);
                console.verbose.println("Ping'n " + url);
                count = 0;
                while (!Stage.ping(uri)) {
                    console.verbose.println("port not ready yet");
                    Thread.sleep(100);
                    count++;
                    if (count > 10 * 60 * 5) {
                        throw new IOException(url + ": ping timed out");
                    }
                }
            }
        }
    }

    private void createBackstage(Stage stage) throws Exception {
        FileNode backstage;

        backstage = stage.getBackstage().mkdirOpt();
        // CAUTION: the log directory is created by "stool create" (because it contains log files)
        for (String dir : new String[] {"ssl", "run" }) {
            backstage.join(dir).mkdirOpt();
        }
    }
}
