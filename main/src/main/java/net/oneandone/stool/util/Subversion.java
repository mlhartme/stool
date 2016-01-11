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
package net.oneandone.stool.util;

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Strings;

import java.io.Writer;

public class Subversion {
    public final String username;
    public final String password;

    public Subversion(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public void checkout(FileNode cwd, String url, String name, Writer dest) throws Failure {
        launcher(cwd, "co", url, name).exec(dest);
    }

    public void update(FileNode cwd, Writer output) throws Failure {
        launcher(cwd, "up").exec(output);
    }

    public String status(FileNode cwd) throws Failure {
        Launcher launcher;

        launcher = launcher(cwd, "status");
        launcher.env("LC_ALL", "C");
        return launcher.exec();
    }

    /** Caution, does not work for nested directories */
    public String probeRootCheckoutUrl(FileNode dir) throws Failure {
        if (dir.join(".svn").isDirectory()) {
            return null;
        } else {
            return checkoutUrl(dir);
        }
    }

    public String checkoutUrl(FileNode dir) throws Failure {
        Launcher launcher;
        String str;
        int idx;

        launcher = launcher(dir, "info");
        launcher.env("LC_ALL", "C");
        str = launcher.exec();
        idx = str.indexOf("URL:") + 4;
        return str.substring(idx, str.indexOf("\n", idx)).trim();
    }

    //--

    private Launcher launcher(FileNode cwd, String... args) {
        Launcher launcher;

        launcher = new Launcher(cwd, "svn");
        launcher.arg(svnCredentials());
        launcher.arg(args);
        return launcher;
    }

    public String[] svnCredentials() {
        return username == null ? Strings.NONE : new String[] {
                        "--no-auth-cache",
                        "--non-interactive", // to avoid password question if svnpassword is wrong
                        "--username", username,
                        "--password", password,
                };
    }
}

