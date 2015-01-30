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

import net.oneandone.sushi.launcher.Failure;
public class StoolProcess {
    private final String command;
    private final String id;
    private final String stage;
    private final long endTime;
    private final String user;
    private final long startTime;
    private final Failure failure;

    public StoolProcess(String command, String id,
      String stage, String user, long startTime, long endTime, Failure failure) {
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
