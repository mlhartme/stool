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
import net.oneandone.stool.util.LogReader;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.cli.Remaining;
import org.joda.time.format.DateTimeFormat;

public class History extends StageCommand {
    public History(Session session) {
        super(session);
    }

    @Option("max")
    private int max = 999;

    /** history entry to show details for */
    private int detail = -1;

    @Remaining
    public void remaining(String str) {
        detail = Integer.parseInt(str);
        max = detail + 1;
    }


    @Override
    public void doInvoke(Stage s) throws Exception {
        String stageId;
        LogEntry entry;
        int counter ;
        String id;
        LogEntry header;

        header = null;
        stageId = s.config().id;
        try (LogReader reader = LogReader.create(session.home.join("logs"))) {
            counter = 0;
            id = null;
            while (true) {
                entry = reader.next();
                if (entry == null) {
                    break;
                }
                if (entry.logger.equals("IN")) {
                    header = entry;
                }
                if (entry.stageId.equals(stageId)) {
                    if (header != null) {
                        counter++;
                        console.info.println("[" + counter + "] " + header.dateTime.toString(DateTimeFormat.shortDateTime()) + " " + header.user + ": " + header.message);
                        id = header.id;
                        header = null;
                    }
                    if (detail == counter && entry.id.equals(id)) {
                        console.info.println("     " + entry.message);
                    }
                    if (counter > max) {
                        break;
                    }
                }
            }
        }
    }
}
