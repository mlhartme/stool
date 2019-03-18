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
import net.oneandone.stool.util.Session;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Start extends StageCommand {
    private final boolean tail;
    private final Map<String, String> environment;
    private final Map<String, Integer> selection;

    public Start(Session session, boolean tail, List<String> selection) {
        super(session, Mode.EXCLUSIVE, Mode.EXCLUSIVE);
        this.tail = tail;
        this.environment = eatEnvironment(selection);
        this.selection = selection(selection);
    }

    private static Map<String, String> eatEnvironment(List<String> selection) {
        Iterator<String> iter;
        String str;
        int idx;
        Map<String, String> result;

        result = new HashMap<>();
        iter = selection.iterator();
        while (iter.hasNext()) {
            str = iter.next();
            idx = str.indexOf('=');
            if (idx == -1) {
                break;
            }
            result.put(str.substring(0, idx), str.substring(idx + 1));
            iter.remove();
        }
        return result;
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
        // to avoid running into a ping timeout below:
        stage.session.configuration.verfiyHostname();
        stage.checkConstraints();
        stage.start(console, environment, selection);
    }

    @Override
    public void doFinish(Stage stage) throws Exception {
        // TODO - to avoid quick start/stop problems; just a ping doesn't solve this, and I don't understand why ...
        stage.awaitStartup(console);
        Thread.sleep(2000);
        console.info.println("Applications available:");
        for (String app : stage.currentMap().keySet()) {
            for (String url : stage.namedUrls(app)) {
                console.info.println("  " + url);
            }
        }
        if (tail) {
            doTail(stage);
        }
    }

    //--

    private void doTail(Stage stage) throws IOException {
        console.info.println("Tailing container output.");
        console.info.println("Press Ctrl-C to abort.");
        console.info.println();
        stage.tailF(console.info);
    }
}
