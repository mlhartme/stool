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
    private final String port;
    private final Integer local;

    public PortForward(Globals globals, String port, Integer local) {
        super(globals);
        this.port = port;
        this.local = local;
    }

    @Override
    public void doMain(Reference reference) throws IOException {
        JsonObject json;
        String server;
        String namespace;
        String pod;
        String token;

        json = reference.client.podToken(reference.stage);
        server = json.get("server").getAsString();
        namespace = json.get("namespace").getAsString();
        pod = json.get("pod").getAsString();
        token = new String(Base64.getDecoder().decode(json.get("token").getAsString()), Charset.forName("US-ASCII"));

        console.verbose.println("server: " + server + ", token: " + token);
        try (OpenShift os = OpenShift.create(server, namespace, token)) {
            try (LocalPortForward pf = os.portForward(pod, Integer.parseInt(port), local == null ? Integer.parseInt(port) : local)) {
                console.info.println("forwarding local port " + pf.getLocalAddress() + " -> pod " + pod + " port " + port);
                console.info.println("Press ctrl-c to stop forwarding.");
                try {
                    Thread.sleep(1800 * 1000);
                } catch (InterruptedException e) {
                    // fall through
                }
            }
        }
    }
}
