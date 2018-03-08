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
import net.oneandone.stool.util.LogEntry;
import net.oneandone.stool.util.LogReader;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.util.Strings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class History extends StageCommand {
    private final boolean details;
    private final int max;

    public History(Session session, boolean details, int max) {
        super(false, session, Mode.NONE, Mode.SHARED, Mode.NONE);
        this.details = details;
        this.max = max;
    }

    @Override
    public void doMain(Stage stage) throws Exception {
        String stageId;
        LogEntry entry;
        Map<String, List<LogEntry>> detailsMap; /* id to it's details */
        LogReader reader;
        List<LogEntry> lst;
        int counter;

        stageId = stage.getId();
        counter = 0;
        detailsMap = new HashMap<>();
        reader = LogReader.create(session.logging.directory());
        while (true) {
            entry = reader.prev();
            if (entry == null) {
                break;
            }
            if (entry.dateTime.plusHours(1).isBefore(stage.created())) {
                // this assumes that creating a stage does not take longer than 1 hour
                break;
            }
            lst = detailsMap.get(entry.id);
            if (lst == null) {
                lst = new ArrayList<>();
                detailsMap.put(entry.id, lst);
            }
            if (entry.logger.equals("COMMAND")) {
                detailsMap.remove(entry.id);
                if (forStage(stageId, lst)) {
                    counter++;
                    console.info.println("[" + LogEntry.FULL_FMT.format(entry.dateTime) + " " + entry.user + "] " + entry.message);
                    if (details) {
                        for (int i = lst.size() - 1; i >= 0; i--) {
                            console.info.println(Strings.indent(lst.get(i).message, "     "));
                        }
                    }
                }
                if (counter == max) {
                    console.info.println("(skipping after " + max + " commands; use -max <n> to see more)");
                    break;
                }
            } else {
                lst.add(entry);
            }
        }
    }

    private static boolean forStage(String stageId, List<LogEntry> lst) {
        for (LogEntry entry : lst) {
            if (stageId.equals(entry.stageId)) {
                return true;
            }
        }
        return false;
    }
}
