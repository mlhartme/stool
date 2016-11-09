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
package net.oneandone.stool.cli;

import net.oneandone.inline.ArgumentException;
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
    private int max;

    /** history entry to show details for */
    private List<Integer> details = new ArrayList<>();

    public History(Session session, int max) {
        super(false, session, Mode.NONE, Mode.SHARED, Mode.NONE);
        this.max = max;
    }

    public void detail(String str) {
        int idx;
        int first;
        int last;

        idx = str.indexOf('-');
        if (idx == -1) {
            try {
                details.add(Integer.parseInt(str));
            } catch (NumberFormatException e) {
                throw new ArgumentException("number expected, got " + str);
            }
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
    public void doMain(Stage s) throws Exception {
        String stageId;
        LogEntry entry;
        Map<String, List<LogEntry>> detailsMap; /* id to it's details */
        LogReader reader;
        List<LogEntry> lst;
        int counter;

        stageId = s.getId();
        counter = 0;
        detailsMap = new HashMap<>();
        reader = LogReader.create(session.logging.directory());
        while (true) {
            entry = reader.prev();
            if (entry == null) {
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
                    console.info.println("[" + counter + "] " + LogEntry.FULL_FMT.format(entry.dateTime) + " " + entry.user + ": " + entry.message);
                    if (details.contains(counter)) {
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
