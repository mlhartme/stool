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
import net.oneandone.stool.util.LogEntry;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.fs.Node;
import org.joda.time.format.DateTimeFormat;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class History extends StageCommand {

    public History(Session session) {
        super(session);
    }

    @Option("max")
    private int max = 999;

    private int detail = -1;

    @Remaining
    public void remaining(String str) {
        detail = Integer.parseInt(str);
        max = detail+1;
    }


    @Override
    public void doInvoke(Stage s) throws Exception {
        List<LogEntry> logEntries;
        logEntries = readLog(s.wrapper.join("shared", "log", "stool.log"));
        int counter = 1;
        UUID uuid = null;
        for (LogEntry entry : logEntries) {
            if (entry.command != null) {
                if (detail == -1 || detail == counter) {
                    console.info.println("["+ counter+ "] " + entry.dateTime.toString(DateTimeFormat.shortDateTime())
                        + " " + entry.user + ": " + entry.command);
                }
                if (detail == counter) {
                    uuid = entry.uuid;
                }
                counter++;
            }
            if (null != uuid && entry.uuid.equals(uuid)) {
                if (entry.out != null) {
                    console.info.println("     " + entry.out);
                }
                if (entry.in != null) {
                    console.info.println("     " + entry.in);
                }
            }

            if (counter > max) {
                break;
            }
        }
    }

    public static List<LogEntry> readLog(Node logfile) throws IOException {
        List<LogEntry> logEntries = new LinkedList<>();
        for (String line : logfile.readLines()) {
            logEntries.add(LogEntry.get(line));
        }
        return logEntries;
    }

}
