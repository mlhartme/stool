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

import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Remaining;

import java.util.ArrayList;
import java.util.List;

public class Sudo extends SessionCommand {
    private final List<String> args = new ArrayList<>();

    @Remaining
    public void args(String arg) {
        args.add(arg);
    }

    public Sudo(Session session) {
        super(session);
    }

    @Override
    public void doInvoke() throws Exception {
        if (args.isEmpty()) {
            throw new ArgumentException("missing command line");
        }
        session.sudo(args.toArray(new String[args.size()]));
    }
}
