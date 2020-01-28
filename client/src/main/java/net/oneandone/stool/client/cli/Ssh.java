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
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;

import java.io.IOException;

public class Ssh extends IteratedStageCommand {
    public Ssh(Globals globals) {
        super(globals);
    }

    @Override
    public void doMain(Reference reference) throws IOException, InterruptedException {
        String str;
        Launcher launcher;
        int result;
        FileNode privateKey;

        str = reference.client.ssh(reference.stage);
        privateKey = world.getTemp().createTempFile();
        privateKey.setPermissions("rwx------");
        privateKey.writeString(str);
        try {
            launcher = world.getWorking().launcher("ssh");
            launcher.arg("stool@" + reference.client.getServer());
            if (console.getVerbose()) {
                launcher.arg("-v");
            }
            launcher.arg("-i");
            launcher.arg(privateKey.getAbsolute());

            launcher.env("SSH_AUTH_SOCK", ""); // make sure not to save keys: disable agent
            console.info.println("starting ssh shell to " +  reference.stage);
            launcher.getBuilder().inheritIO();
            result = launcher.getBuilder().start().waitFor();
            console.verbose.println("result: " + result);
        } finally {
            privateKey.deleteFile();
        }
    }
}
