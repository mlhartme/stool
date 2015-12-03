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
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Remaining;

import java.util.HashMap;
import java.util.Map;

public class Port extends StageCommand {
    private final Map<String, Integer> ports = new HashMap<>();

    public Port(Session session) {
        super(session, Mode.EXCLUSIVE, Mode.SHARED, Mode.NONE);
    }

    @Remaining
    public void port(String str) {
        int idx;
        int port;
        String vhost;

        idx = str.indexOf('=');
        if (idx == -1) {
            throw new ArgumentException("missing = in argument '" + str + "'");
        }
        vhost = str.substring(0, idx);
        port = Integer.parseInt(str.substring(idx + 1));
        if (port % 2 != 0) {
            throw new ArgumentException("even port number expected: " + port);
        }
        if (ports.put(vhost, port) != null) {
            throw new ArgumentException("duplicate name: " + vhost);
        }
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        session.pool().allocate(stage, ports);
    }
}
