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
package net.oneandone.stool.cli.command;

import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.cli.Reference;
import net.oneandone.stool.core.HistoryEntry;

public class History extends IteratedStageCommand {
    public History(Globals globals, String stage) {
        super(globals, stage);
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        HistoryEntry entry;

        for (String line : reference.client.history(reference.stage)) {
            if (console.getVerbose()) {
                console.info.println(line);
            } else {
                entry = HistoryEntry.parse(line);
                console.info.printf("%s %s %s\n", HistoryEntry.SIMPLE_DATE_FMT.format(entry.dateTime), entry.user, entry.command);
            }
        }
    }
}
