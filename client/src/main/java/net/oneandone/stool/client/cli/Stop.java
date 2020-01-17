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

import java.util.ArrayList;
import java.util.List;

public class Stop extends IteratedStageCommand {
    private final List<String> apps;

    public Stop(Globals globals) {
        this(globals, new ArrayList<>());
    }
    public Stop(Globals globals, List<String> apps) {
        super(globals);
        this.apps = apps;
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        doNormal(reference);
    }

    public void doNormal(Reference reference) throws Exception {
        List<String> stopped;

        stopped = reference.client.stop(reference.stage, apps);
        console.info.println("stopped " + stopped);
        stopped = Start.removeTag(stopped);
        for (String app : apps) {
            if (!stopped.contains(app)) {
                console.info.println("note: " + app + " was already down");
            }
        }
    }
}
