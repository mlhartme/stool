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
package net.oneandone.stool.setup;

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DebianDashboardSetup extends Debian {
    public static void main(String[] args) {
        System.exit(new DebianDashboardSetup().run(args));
    }

    //--

    private static String get(String name) {
        String result;

        result = System.getenv("STOOL_SETUP_" + name.toUpperCase());
        if (result == null) {
            throw new IllegalStateException(name);
        }
        return result;
    }

    private final FileNode home;
    private final String group;
    private final String user;

    public DebianDashboardSetup() {
        home = world.file(get("home"));
        group = get("group");
        user = get("user");
    }

    @Override
    public void postinstConfigure() throws IOException {
        setupUser();
    }

    private void setupUser() throws IOException {
        boolean existing;
        boolean inGroup;

        existing = test("id", "-u", user);
        if (!existing) {
            if (world.file("/home").join(user).isDirectory()) {
                throw new IOException("cannot create user " + user + ": home directory already exists");
            }
            verbose(slurp("adduser", "--system", "--ingroup", group, "--home", "/home/" + user, user));
        }

        inGroup = groups(user).contains(group);
        if (!inGroup) {
            exec("usermod", "-a", "-G", group, user);
        }
        echo("user: " + user + " (" + (existing ? "existing" : "created") + (inGroup ? "" : ", added to group" + group) + ")");
    }
}
