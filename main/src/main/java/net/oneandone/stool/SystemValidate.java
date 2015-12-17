/**
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
package net.oneandone.stool;

import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;


public class SystemValidate extends SessionCommand {
    private final Session session;

    public SystemValidate(Session session) {
        super(session, Mode.EXCLUSIVE);
        this.session = session;
    }

    @Override
    public void doInvoke() throws Exception {
        checkHostname();
    }

    private void checkHostname() throws Failure {
        String ip;
        String subDomain;

        ip = digIp(session.configuration.hostname);
        if (ip.isEmpty()) {
            console.error.println("missing dns entry for " + session.configuration.hostname);
        }
        subDomain = digIp("foo." + session.configuration.hostname);
        if (subDomain.isEmpty() || !subDomain.endsWith(ip)) {
            console.error.println("missing dns * entry for " + session.configuration.hostname + " (" + subDomain + ")");
        }
    }

    private String digIp(String name) throws Failure {
        Launcher dig;

        dig = new Launcher((FileNode) console.world.getWorking(), "dig", "+short", name);
        return dig.exec().trim();
    }
}
