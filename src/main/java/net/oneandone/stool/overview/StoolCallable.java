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
package net.oneandone.stool.overview;

import com.google.gson.Gson;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;

import java.io.IOException;
import java.util.concurrent.Callable;

public class StoolCallable implements Callable<StoolCallable.StoolProcess> {
    public static StoolCallable create(Stage stage, String command, String options, String id, FileNode logs) throws IOException {
        Session session;

        session = stage.session;
        return new StoolCallable(session.home, session.gson, command, options, stage.getName(), id, logs,
                session.configuration.security.isShared(), stage.owner());
    }

    private final FileNode home;
    private final Gson gson;
    private final String command;
    private final String options;
    private final String id;
    private final String stage;
    private final FileNode logDir;
    private final boolean su;
    private final String runAs;

    public StoolCallable(FileNode home, Gson gson, String command, String options, String stage, String id, FileNode logDir, boolean su, String runAs) {
        this.home = home;
        this.gson = gson;
        this.command = command;
        this.options = options;
        this.id = id;
        this.stage = stage;
        this.logDir = logDir;
        this.su = su;
        this.runAs = runAs;
    }

    @Override
    public StoolProcess call() throws Exception {
        StoolProcess stoolProcess;
        StringBuilder builder;
        FileNode script;
        FileNode stat;
        Failure failure;
        long startTime;

        builder = new StringBuilder();
        failure = null;
        startTime = 0L;
        long endTime = 0;
        stat = logDir.join(id + ".stat").mkfile();
        builder.append("#!/bin/sh\n").append("#runAs:").append(runAs).append("\n");
        if (su) {
            builder.append("sudo -u ").append(runAs).append(" -E ");
       }
        builder.append(home.join("bin/stool-raw.sh").getAbsolute()).append(' ').append(command).append(" -stage ").append(stage);
        if (options != null && !options.equals("")) {
            builder.append(" ").append(options);
        }
        builder.append(" >> ").append(logDir.getAbsolute()).append("/").append(id).append(".log").append("\"\n");
        script = logDir.join(id + ".sh").mkfile();
        script.writeString(builder.toString()).setPermissions("rwx------");
        try {
            stat.writeString(gson.toJson(new StoolProcess(command, id, stage, runAs, startTime, endTime, failure)));
            startTime = System.currentTimeMillis();
            script.launcher("sh", script.getAbsolute()).dir(script.getParent()).exec();
            endTime = System.currentTimeMillis();
        } catch (Failure e) {
            failure = e;
        }
        stoolProcess = new StoolProcess(command, id, stage, runAs, startTime, endTime, failure);
        stat.writeString(gson.toJson(stoolProcess));
        return stoolProcess;
    }

    public static class StoolProcess {
        private final String command;
        private final String id;
        private final String stage;
        private final long endTime;
        private final String user;
        private final long startTime;
        private final Failure failure;

        public StoolProcess(String command, String id, String stage, String user, long startTime, long endTime, Failure failure) {
            this.command = command;
            this.id = id;
            this.stage = stage;
            this.user = user;
            this.startTime = startTime;
            this.endTime = endTime;
            this.failure = failure;
        }

        public String getCommand() {
            return command;
        }
        public String getId() {
            return id;
        }
        public String getStage() {
            return stage;
        }
        public long getEndTime() {
            return endTime;
        }
        public long getStartTime() {
            return startTime;
        }
        public String getFailure() {
            return failure.getMessage();
        }
        public String getUser() {
            return user;
        }
    }
}
