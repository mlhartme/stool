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
package net.oneandone.stool.dashboard.config;

import net.oneandone.stool.dashboard.StoolCallable;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;

import java.util.TimerTask;
import java.util.UUID;

public class RefreshTask extends TimerTask {
    private final FileNode jar;
    private final Session session;
    private final FileNode logs;

    public RefreshTask(FileNode jar, Session session, FileNode logs) {
        this.jar = jar;
        this.session = session;
        this.logs = logs;
    }

    @Override
    public void run() {
        try {
            for (Stage stage : session.listWithoutSystem()) {
                if (stage.config().autoRefresh) {
                    StoolCallable.create(jar, UUID.randomUUID().toString(), logs, stage, "refresh", "-autorestart", "-autorechown").call();
                }
            }
        } catch (Exception e) {
            session.reportException("RefreshTask", e);
        }
    }
}
