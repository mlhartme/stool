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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Start extends IteratedStageCommand {
    private final int http;
    private final int https;
    private final Map<String, String> environment;
    private final Map<String, String> selection;

    public Start(Globals globals, List<String> selection) {
        this(globals, -1, -1, selection);
    }

    public Start(Globals globals, int http, int https, List<String> selection) {
        super(globals);
        this.http = http;
        this.https = https;
        this.environment = new HashMap<>();

        eatEnvironment(selection, environment);

        this.selection = selection(selection);
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
        List<String> started;

        started = reference.client.start(reference.stage, http, https, environment, selection);
        console.info.println("starting " + started + " ...");
        started = removeTag(started);
        for (String app : selection.keySet()) {
            if (!started.contains(app)) {
                console.info.println("note: " + app + " was already up");
            }
        }
    }

    public static List<String> removeTag(List<String> appsWithTag) {
        List<String> result;
        int idx;

        result = new ArrayList<>(appsWithTag.size());
        for (String appWithTag : appsWithTag) {
            idx = appWithTag.indexOf(':');
            if (idx == -1) {
                throw new IllegalStateException("missing tag: " + appWithTag);
            }
            result.add(appWithTag.substring(0, idx));
        }
        return result;
    }

    @Override
    public void doFinish(Reference reference) throws Exception {
        Map<String, List<String>> running;

        running = reference.client.awaitStartup(reference.stage);
        console.info.println("Applications available:");
        for (String app : running.keySet()) {
            for (String url : running.get(app)) {
                console.info.println("  " + url);
            }
        }
    }
}
