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
import net.oneandone.sushi.util.Strings;

import java.io.IOException;

public class DebianDashboardSetup extends Debian {
    public static void main(String[] args) throws IOException {
        System.exit(new DebianDashboardSetup().run(args));
    }

    //--

    private final FileNode lib;
    private final String group;
    private final String user;
    private final String port;
    private final String svnuser;
    private final String svnpassword;

    public DebianDashboardSetup() throws IOException {
        super("stool"); // share log file with stool, to see timing
        lib = world.file(db_get("stool/lib"));
        group = db_get("stool/group");

        user = db_get("stool-dashboard/user");
        port = db_get("stool-dashboard/port");
        svnuser = db_get("stool-dashboard/svnuser");
        svnpassword = db_get("stool-dashboard/svnpassword");
    }

    @Override
    public void prermRemove() throws IOException {
        log(stool("remove", "-autostop", "-batch", "-stage", "dashboard"));
    }

    @Override
    public void prermUpgrade() throws IOException {
        prermRemove();
    }

    @Override
    public void postinstConfigure(String previous) throws IOException {
        setupUser();
        log(stool("create", "file:///usr/share/stool-dashboard/dashboard.war", lib.join("dashboard").getAbsolute(), "until=reserved"));
        if (!port.isEmpty()) {
            log(stool("port", "-stage", "dashboard", "dashboard:" + port));
        }
        properties();
        log(stool("start", "-stage", "dashboard"));
    }

    //--

    private String stool(String ... cmd) throws IOException {
        return slurp(Strings.append(new String[] {"sudo", "-u", user, "/usr/share/stool/stool-raw.sh"}, cmd));
    }

    private void properties() throws IOException {
        FileNode properties;

        properties = lib.join("dashboard.properties");
        if (properties.isFile()) {
            log("reusing existing configuration: " + properties);
        } else {
            properties.writeLines(
                    "svnuser=" + svnuser,
                    "svnpassword=" + svnpassword
            );
            exec("chown", user, properties.getAbsolute());
            exec("chmod", "600", properties.getAbsolute());
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
            log(slurp("adduser", "--system", "--ingroup", group, "--home", "/home/" + user, user));
        }

        inGroup = groups(user).contains(group);
        if (!inGroup) {
            exec("usermod", "-a", "-G", group, user);
        }
        log("user: " + user + " (" + (existing ? "existing" : "created") + (inGroup ? "" : ", added to group" + group) + ")");
    }
}
