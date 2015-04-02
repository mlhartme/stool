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
package net.oneandone.stool.overview.config;

import net.oneandone.stool.Chown;
import net.oneandone.stool.Refresh;
import net.oneandone.stool.overview.StoolCallable;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;
import net.oneandone.stool.util.Slf4jOutputStream;
import net.oneandone.sushi.fs.file.FileNode;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.TimerTask;
import java.util.UUID;

public class PrepareRefresh extends TimerTask {
    private final Session session;
    private final FileNode logs;
    private final PrintWriter printWriter;

    public PrepareRefresh(Session session, FileNode logs) {
        this.session = session;
        this.logs = logs;
        this.printWriter = new PrintWriter(new Slf4jOutputStream(LoggerFactory.getLogger(PrepareRefresh.class), false));
    }

    @Override
    public void run() {
        Refresh refresh;
        Chown chown;

        try {
            for (Stage stage : session.listWithoutOverview()) {
                if (stage.config().autoRefresh) {
                    if (!stage.owner().equals(session.user)) {
                        chown = new Chown(session, true);
                        chown.doInvoke(stage);
                    }
                    refresh = new Refresh(session, true, true, false);
                    refresh.prepare(stage);
                    if (stage.updateAvailable()) {
                        executeUpdate(stage);
                    }
                }
            }
        } catch (Exception e) {
            printWriter.println("Error while preparing refresh: " + e.getCause());
            e.printStackTrace(printWriter);
        }
    }

    public void executeUpdate(Stage stage) throws Exception {
        StoolCallable.create(UUID.randomUUID().toString(), logs, stage, "refresh", "-usePrepared").call();
    }
}
