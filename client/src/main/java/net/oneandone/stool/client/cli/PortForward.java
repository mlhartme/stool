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

import java.io.IOException;

public class PortForward extends IteratedStageCommand {
    private final String port;
    private final Integer local;

    public PortForward(Globals globals, String port, Integer local) {
        super(globals);
        this.port = port;
        this.local = local;
    }

    @Override
    public void doMain(Reference reference) throws IOException {
        String token;

        token = reference.client.podToken(reference.stage);
        System.out.println("token: " + token);
    }
}
