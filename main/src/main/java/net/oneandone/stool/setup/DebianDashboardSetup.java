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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

public class DebianDashboardSetup extends Debian {
    public static void main(String[] args) throws IOException {
        int result;

        try (PrintWriter out = new PrintWriter(new FileOutputStream("/tmp/dpkg-stool-dashboard.log", true))) {
            result = new DebianDashboardSetup(out).run(args);
        }
        System.exit(result);
    }

    //--

    private final FileNode system;
    private final String user;
    private final String port;
    private final String svnuser;
    private final String svnpassword;

    public DebianDashboardSetup(PrintWriter out) throws IOException {
        super(out); // share log file with stool, to see timing
        system = world.file("/usr/share/stool-3.4/system");
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
        log(stool("create", "file:///usr/share/stool-3.4-dashboard/dashboard.war", system.join("dashboard").getAbsolute(), "expire=never",
                "url=https://%a.%s.%h:%p"));
        if (!port.isEmpty()) {
            log(stool("port", "-stage", "dashboard", "dashboard=" + port));
        }
        properties();
        log(stool("start", "-stage", "dashboard"));
    }

    //--

    private String stool(String ... cmd) throws IOException {
        return slurp(Strings.append(new String[] {"sudo", "-u", user, "/usr/bin/stool"}, cmd));
    }

    private void properties() throws IOException {
        FileNode properties;

        properties = system.join("dashboard.properties");
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
}
