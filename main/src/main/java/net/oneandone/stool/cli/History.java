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
import net.oneandone.stool.stage.Reference;
import net.oneandone.stool.util.Server;

public class History extends StageCommand {
    private final boolean details;
    private final int max;

    public History(Server server, boolean details, int max) {
        super(server, Mode.NONE, Mode.SHARED);
        this.details = details;
        this.max = max;
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        for (String line : server.history(reference, details, max)) {
            console.info.println(line);
        }
    }
}
