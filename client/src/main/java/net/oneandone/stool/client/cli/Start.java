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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Start extends IteratedStageCommand {
    private final int http;
    private final int https;
    private final Map<String, String> environment;
    private final String image;

    public Start(Globals globals, List<String> args) {
        this(globals, -1, -1, args);
    }

    public Start(Globals globals, int http, int https, List<String> args) {
        super(globals);
        this.http = http;
        this.https = https;
        this.environment = new HashMap<>();

        eatEnvironment(args, environment);

        this.image = imageOpt(args);
    }

    private static void eatEnvironment(List<String> selection, Map<String, String> dest) {
        Iterator<String> iter;
        String str;
        int idx;

        iter = selection.iterator();
        while (iter.hasNext()) {
            str = iter.next();
            idx = str.indexOf('=');
            if (idx == -1) {
                break;
            }
            dest.put(str.substring(0, idx), str.substring(idx + 1));
            iter.remove();
        }
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        String starting;

        starting = reference.client.start(reference.stage, image, http, https, environment);
        console.info.println("starting image " + starting + " ...");
    }

    @Override
    public void doFinish(Reference reference) throws Exception {
        Map<String, String> running;

        running = reference.client.awaitStartup(reference.stage);
        console.info.println("Applications available:");
        for (Map.Entry<String, String> entry : running.entrySet()) {
            console.info.println("  " + entry.getKey() + " " + entry.getValue());
        }
    }
}
