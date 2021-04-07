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
package net.oneandone.stool.cli.command;

import io.fabric8.kubernetes.client.LocalPortForward;
import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.cli.PodConfig;
import net.oneandone.stool.cli.Reference;
import net.oneandone.stool.kubernetes.Engine;

import java.io.IOException;

public class PortForward extends IteratedStageCommand {
    /** minutes */
    private final int timeout;
    private final int localPort;
    private final int podPort;

    public PortForward(Globals globals, int timeout, String stage, int port, Integer toPort) {
        super(globals, stage);
        this.timeout = timeout;
        this.localPort = port;
        this.podPort = toPort == null ? port : toPort.intValue();
    }

    @Override
    public void doMain(Reference reference) throws IOException {
        PodConfig config;

        config = reference.client.podToken(reference.stage, timeout);
        console.verbose.println(config.toString());
        try (Engine engine = Engine.create(globals.settings().local.json, config)) {
            try (LocalPortForward pf = engine.podPortForward(config.pod, localPort, podPort)) {
                console.info.println("forwarding local port " + pf.getLocalPort() + " -> pod " + config.pod + " port " + podPort);
                console.info.println("for " + timeout + " minutes");
                console.info.println("Press ctrl-c abort.");
                try {
                    Thread.sleep(timeout * 60 * 1000);
                } catch (InterruptedException e) {
                    console.info.println("aborted");
                    // fall through
                }
            }
        }
    }
}
