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
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Reference;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;

import java.io.IOException;

public class Tunnel extends StageCommand {
    private final String app;
    private final String port;
    private final Integer local;

    public Tunnel(Globals globals, String app, String port, Integer local) {
        super(globals);
        this.app = app;
        this.port = port;
        this.local = local;
    }

    // http://www.netzmafia.de/skripten/internet/ssh-tunnel.html

    @Override
    public void doMain(Reference reference) throws IOException, InterruptedException {
        JsonObject tunnel;
        int remotePort;
        int localPort;
        Launcher launcher;
        int result;
        FileNode privateKey;

        tunnel = reference.client.tunnel(reference.stage, app, port);
        remotePort = tunnel.get("port").getAsInt();
        privateKey = world.getTemp().createTempFile();
        privateKey.setPermissions("rwx------");
        privateKey.writeString(tunnel.get("privateKey").getAsString());
        try {
            launcher = world.getWorking().launcher("ssh");
            launcher.arg("stool@" + reference.client.getServer());
            localPort = local == null ? remotePort : local;
            if (console.getVerbose()) {
                launcher.arg("-v");
            }
            launcher.arg("-i");
            launcher.arg(privateKey.getAbsolute());
            launcher.arg("-L");
            launcher.arg(localPort + ":localhost:" + remotePort);

            launcher.env("SSH_AUTH_SOCK", ""); // make sure not to save keys: disable agent
            console.verbose.println("starting " + launcher + " ...");
            console.info.println("starting tunnel to localhost:" + localPort + ", press ctrl-c to stop");
            launcher.getBuilder().inheritIO();
            result = launcher.getBuilder().start().waitFor();
            console.verbose.println("result: " + result);
        } finally {
            privateKey.deleteFile();
        }
    }
}