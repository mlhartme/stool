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

import java.util.List;

/**
 * From a user's perspective, this behaves like a stage command; technocally, it is not because it easier to perfrom all validation
 * in a single server call.
 */
public class Validate extends StageCommand {
    private final boolean email;
    private final boolean repair;

    public Validate(Globals globals, boolean email, boolean repair) {
        super(globals);
        this.email = email;
        this.repair = repair;
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        List<String> result;

        result = reference.client.validate(reference.stage, email, repair);
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
