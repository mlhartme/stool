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

import net.oneandone.stool.server.util.Server;

import java.util.List;

/** Currently not stage stage event because the server performs validation as a single operation */
public class Validate extends ClientCommand {
    private final boolean email;
    private final boolean repair;
    private final String stageClause;

    public Validate(Server server, boolean email, boolean repair, String stageClause) {
        super(server);
        this.email = email;
        this.repair = repair;
        this.stageClause = stageClause;
    }

    @Override
    public void doRun() throws Exception {
        List<String> result;

        result = server.validate(stageClause, email, repair);
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
