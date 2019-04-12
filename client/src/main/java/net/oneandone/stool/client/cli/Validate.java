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

import java.util.List;

/**
 * From a user's perspective, this behaves like a stage command; technocally, it is not because it easier to perfrom all validation
 * in a single server call.
 */
public class Validate extends ClientCommand {
    private final boolean email;
    private final boolean repair;
    private final String stageClause;

    public Validate(World world, Console console, Client client, boolean email, boolean repair, String stageClause) {
        super(world, console, client);
        this.email = email;
        this.repair = repair;
        this.stageClause = stageClause;
    }

    @Override
    public void doRun() throws Exception {
        List<String> result;

        result = client.validate(stageClause, email, repair);
        if (result.isEmpty()) {
            console.info.println("validate ok");
        } else {
            for (String line : result) {
                console.info.println(line);
            }
            console.info.println();
            console.info.println("validate failed");
        }
    }
}