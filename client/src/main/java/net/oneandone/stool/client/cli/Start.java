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

import net.oneandone.inline.Console;
import net.oneandone.stool.client.Client;
import net.oneandone.sushi.fs.World;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Start extends StageCommand {
    private final boolean tail;
    private final int http;
    private final int https;
    private final Map<String, String> environment;
    private final Map<String, Integer> selection;

    public Start(Globals globals, World world, Console console, boolean tail, List<String> selection) {
        this(globals, world, console, tail, -1, -1, selection);
    }

    public Start(Globals globals, World world, Console console, boolean tail, int http, int https, List<String> selection) {
        super(globals, world, console);
        this.tail = tail;
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
    public void doMain(Client client, String stage) throws Exception {
        client.start(stage, http, https, environment, selection);
    }

    @Override
    public void doFinish(Client client, String stage) throws Exception {
        Map<String, List<String>> running;

        running = client.awaitStartup(stage);
        console.info.println("Applications available:");
        for (String app : running.keySet()) {
            for (String url : running.get(app)) {
                console.info.println("  " + url);
            }
        }
        if (tail) {
            doTail(stage);
        }
    }

    //--

    private void doTail(String stage) throws IOException {
        console.info.println("Tailing container output.");
        console.info.println("Press Ctrl-C to abort.");
        console.info.println();
        // TODO: stage.tailF(console.info);
    }
}
