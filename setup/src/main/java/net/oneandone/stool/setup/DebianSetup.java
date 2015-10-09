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

public class DebianSetup extends Debian {
    public static void main(String[] args) {
        System.exit(new DebianSetup().run(args));
    }

    //--

    private final FileNode bin;
    private final FileNode home;
    private final String group;
    private final String user;

    public DebianSetup() {
        bin = world.file("/usr/share/stool");
        // TODO replace this check by some kind of configuration
        if (world.file("/opt/ui/opt/tools").isDirectory()) {
            home = world.file("/opt/ui/opt/tools/stool");
            group = "users";
        } else {
            home = world.file("/var/lib/stool");
            group = "stool";
        }
        user = group;
    }

    //--

    @Override
    public void postinstConfigure() throws IOException {
        setupGroup();
        setupUser();
        setupHome();
        home.link(bin.join("home"));
        verbose(slurp("sudo", "-u", user, bin.join("stool-raw.sh").getAbsolute(), "chown", "-stage", "dashboard"));
        exec("update-rc.d", "stool", "defaults");
    }

    @Override
    public void prermRemove() throws IOException {
        echo(slurp("service", "stool", "stop"));
        bin.join("home").deleteDirectory();
    }

    @Override
    public void prermUpgrade() throws IOException {
        // TODO: upgrade could be much cheaper:
        // * block new stool invocations
        // * stop stool dashboard

        // may fail if setup did not complete properly
        prermRemove();
    }

    @Override
    public void postrmPurge() throws IOException {
        exec("update-rc.d", "stool", "remove");
        home.deleteTree();
    }

    //--

    private void setupGroup() throws IOException {
        List<String> result;

        if (test("getent", "group", group)) {
            echo("group: " + group + " (existing)");
        } else {
            result = new ArrayList<>();
            exec("groupadd", group);
            for (FileNode dir : world.file("/home/").list()) {
                if (dir.isDirectory()) {
                    String name = dir.getName();
                    if (test("id", "-u", name)) {
                        result.add(name);
                        exec("usermod", "-a", "-G", group, name);
                    }
                }
            }
            echo("group: " + group + " (created with " + Separator.SPACE.join(result) + ")");
        }
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

    //--

    public void setupHome() throws IOException {
        Home h;

        h = new Home(console, home, true, new HashMap<>());
        if (home.exists()) {
            h.upgrade();
            echo("home: " + home.getAbsolute() + " (upgraded)");
        } else {
            console.info.println("creating home: " + home);
            try {
                h.create();
            } catch (IOException e) {
                // make sure we don't leave any undefined home directory;
                try {
                    home.deleteTreeOpt();
                } catch (IOException suppressed) {
                    e.addSuppressed(suppressed);
                }
                throw e;
            }
            exec("chmod", "g+s", "-R", home.getAbsolute());
            exec("chgrp", group, "-R", home.getAbsolute());
        }
    }
}
