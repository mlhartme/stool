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

import com.google.gson.JsonObject;
import io.fabric8.kubernetes.client.LocalPortForward;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.OpenShift;
import net.oneandone.stool.client.Reference;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Base64;

public class PortForward extends IteratedStageCommand {
    /** minutes */
    private final int timeout;
    private final int localPort;
    private final int podPort;

    public PortForward(Globals globals, int timeout, int port, Integer toPort) {
        super(globals);
        this.timeout = timeout;
        this.localPort = port;
        this.podPort = toPort == null ? port : toPort.intValue();
    }

    @Override
    public void doMain(Reference reference) throws IOException {
        JsonObject json;
        String server;
        String namespace;
        String pod;
        String token;

        json = reference.client.podToken(reference.stage, timeout);
        server = json.get("server").getAsString();
        namespace = json.get("namespace").getAsString();
        pod = json.get("pod").getAsString();
        token = new String(Base64.getDecoder().decode(json.get("token").getAsString()), Charset.forName("US-ASCII"));

        console.verbose.println("server: " + server + ", token: " + token);
        try (OpenShift os = OpenShift.create(server, namespace, token)) {
            try (LocalPortForward pf = os.portForward(pod, localPort, podPort)) {
                console.info.println("forwarding local port " + pf.getLocalPort() + " -> pod " + pod + " port " + podPort);
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
