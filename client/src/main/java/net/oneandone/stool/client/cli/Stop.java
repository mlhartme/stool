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

import java.util.ArrayList;
import java.util.List;

public class Stop extends StageCommand {
    private final List<String> apps;

    public Stop(World world, Console console, Client client) {
        this(world, console, client, new ArrayList<>());
    }
    public Stop(World world, Console console, Client client, List<String> apps) {
        super(world, console, client);
        this.apps = apps;
    }

    @Override
    public void doMain(String stage) throws Exception {
        doNormal(stage);
    }

    public void doNormal(String stage) throws Exception {
        client.stop(stage, apps);
    }

    @Override
    public void doFinish(String stage) {
        console.info.println("state: down");
    }
}
