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
import net.oneandone.stool.common.Reference;
import net.oneandone.stool.client.Server;
import net.oneandone.sushi.fs.World;

public class History extends StageCommand {
    private final boolean details;
    private final int max;

    public History(World world, Console console, Server server, boolean details, int max) {
        super(world, console, server);
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
