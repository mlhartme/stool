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

import io.fabric8.kubernetes.client.dsl.ExecWatch;
import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.cli.PodConfig;
import net.oneandone.stool.cli.Reference;
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.kubernetes.StoolExecListener;

import java.io.IOException;

public class Ssh extends IteratedStageCommand {
    private final int timeout;
    private final String shell;

    public Ssh(Globals globals, int timeout, String stage, String shell) {
        super(globals, stage);
        this.timeout = timeout;
        this.shell = shell == null ? "/bin/sh" : shell;
    }

    @Override
    public void doMain(Reference reference) throws IOException, InterruptedException {
        PodConfig config;
        StoolExecListener listener;

        config = reference.client.podToken(reference.stage, timeout);
        console.verbose.println(config.toString());
        try (Engine os = Engine.create(globals.configuration().json, config)) {
            listener = new StoolExecListener();
            try (ExecWatch watch = os.ssh(config.pod, "main", new String[] { shell }, listener)) {
                while (listener.closeReason == null) { // TODO: busy wait
                    Thread.sleep(100);
                }
                console.verbose.println("closed, code=" + listener.closeCode + ", reason=" + listener.closeReason);
            }
        }
    }
}
