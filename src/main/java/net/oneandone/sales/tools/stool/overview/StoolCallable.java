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
package net.oneandone.sales.tools.stool.overview;

import com.google.gson.Gson;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;

import java.util.concurrent.Callable;
public class StoolCallable implements Callable<StoolProcess> {
    private final String command;
    private final String options;
    private final String id;
    private final String stage;
    private final FileNode logDir;
    private final boolean own;
    private String user;

    public StoolCallable(String command, String options, String stage, String user, String id, FileNode logDir, boolean own) {
        this.command = command;
        this.options = options;
        this.user = user;
        this.id = id;
        this.stage = stage;
        this.logDir = logDir;
        this.own = own;
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
        builder.append("#!/bin/sh\n").append("#User:").append(user).append("\n");
        if (own) {
            builder.append("bash --login -c \"stool chown -stage ").append(stage).append(" -overview\"\n");
        }
        builder.append("bash --login -c \"stool ").append(command).append(" -stage ").append(stage);
        if (options != null && !options.equals("")) {
            builder.append(" ").append(options);
        }
        builder.append(" >> ").append(logDir.getAbsolute()).append("/")
          .append(id).append(".log").append("\"");
        script = logDir.join(id + ".sh").mkfile();
        script.writeString(builder.toString()).setPermissions("rwx------");
        try {
            stoolProcess = new StoolProcess(command, id, stage, user, startTime, endTime, failure);
            stat.writeString(new Gson().toJson(stoolProcess));
            startTime = System.currentTimeMillis();
            script.launcher("sh", script.getAbsolute()).dir(script.getParent()).exec();
            endTime = System.currentTimeMillis();
        } catch (Failure e) {
            failure = e;

        }
        stoolProcess = new StoolProcess(command, id, stage, user, startTime, endTime, failure);
        stat.writeString(new Gson().toJson(stoolProcess));
        return stoolProcess;
    }
}
