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

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.Reference;
import net.oneandone.stool.server.util.Server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class App extends StageCommand {
    private final List<String> names;

    public App(Server server, List<String> names) {
        super(server);
        this.names = names;
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        for (String app : appSelection(server.apps(reference))) {
            for (String line : server.appInfo(reference, app)) {
                console.info.println(line);
            }
        }
    }

    private List<String> appSelection(Collection<String> available) {
        List<String> result;

        if (names.isEmpty()) {
            result = new ArrayList<>(available);
            Collections.sort(result);
        } else {
            result = new ArrayList<>();
            for (String name : names) {
                if (available.contains(name)) {
                    result.add(name);
                } else {
                    throw new ArgumentException("unknown app: " + name);
                }
            }
        }
        return result;
    }
}
