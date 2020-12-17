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
package net.oneandone.stool.client.cli;

import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Reference;

public class History extends IteratedStageCommand {
    private final boolean details;
    private final int max;

    public History(Globals globals, boolean details, int max) {
        super(globals);
        this.details = details;
        this.max = max;
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        for (String line : reference.client.history(reference.stage, details, max)) {
            console.info.println(line);
        }
    }
}