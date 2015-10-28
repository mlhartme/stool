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
package net.oneandone.stool;

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.locking.Lock;
import net.oneandone.stool.util.LogEntry;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.cli.Remaining;

import java.util.ArrayList;
import java.util.List;

public class History extends StageCommand {
    public History(Session session) {
        super(session, Lock.Mode.SHARED, Lock.Mode.SHARED, Lock.Mode.NONE);
    }

    @Option("max")
    private int max = 999;

    /** history entry to show details for */
    private List<Integer> details = new ArrayList<>();

    @Remaining
    public void remaining(String str) {
        int idx;
        int first;
        int last;

        idx = str.indexOf('-');
        if (idx == -1) {
            details.add(Integer.parseInt(str));
        } else {
            first = idx == 0 ? 1 : Integer.parseInt(str.substring(0, idx));
            last = idx == str.length() - 1 ? max : Integer.parseInt(str.substring(idx + 1));
            if (first > last) {
                throw new ArgumentException("invalid range: " + first + "-" + last);
            }
            for (int i = first; i <= last; i++) {
                details.add(i);
            }
        }
    }

    @Override
    public void doInvoke(Stage s) throws Exception {
        String stageId;
        int counter;
        int remove;
        List<LogEntry> commands;

        stageId = s.config().id;
        commands = session.logging.stageCommands(stageId);
        if (commands.size() > max) {
            remove = commands.size() - max;
            console.info.println("(max entries reached: " + max + ", ignoring " + remove + " older commands)");
            while (remove > 0) {
                commands.remove(0);
                remove--;
            }
        }
        counter = 0;
        for (LogEntry command : commands) {
            console.info.println("[" + ++counter + "] " + LogEntry.FMT.format(command.dateTime) + " " + command.user + ": " + command.message);
            if (details.contains(counter)) {
                for (LogEntry entry : session.logging.info(stageId, command.id)) {
                    console.info.println("     " + entry.message);
                }
            }
        }
    }
}
