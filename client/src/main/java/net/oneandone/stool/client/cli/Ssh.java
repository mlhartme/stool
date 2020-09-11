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

import io.fabric8.kubernetes.client.dsl.ExecWatch;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.OpenShift;
import net.oneandone.stool.client.PodConfig;
import net.oneandone.stool.client.Reference;

import java.io.IOException;

public class Ssh extends IteratedStageCommand {
    private final int timeout;

    public Ssh(Globals globals, int timeout) {
        super(globals);
        this.timeout = timeout;
    }

    @Override
    public void doMain(Reference reference) throws IOException, InterruptedException {
        PodConfig config;
        OpenShift.StoolExecListener listener;

        config = reference.client.podToken(reference.stage, timeout);
        console.verbose.println(config.toString());
        try (OpenShift os = OpenShift.create(config)) {
            listener = new OpenShift.StoolExecListener();
            try (ExecWatch watch = os.ssh(config.pod, listener)) {
                while (listener.closeReason == null) { // TODO: budy wait
                    Thread.sleep(100);
                }
                console.verbose.println("closed, code=" + listener.closeCode + ", reason=" + listener.closeReason);
            }
        }
    }
}
