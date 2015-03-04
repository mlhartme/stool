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

import java.io.Writer;

public class Subversion {
    private final String username;
    private final String password;

    public Subversion(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public void checkout(FileNode cwd, String url, String name, Writer dest) throws Failure {
        interactive(cwd, "co", url, name).exec(dest);
    }

    public void update(FileNode cwd, Writer output) throws Failure {
        interactive(cwd, "up").exec(output);
    }

    public String status(FileNode cwd) throws Failure {
        Launcher launcher;

        launcher = launcher(false, cwd, "status");
        launcher.env("LC_ALL", "C");
        return launcher.exec();
    }

    public String checkoutUrl(FileNode cwd) throws Failure {
        Launcher launcher;
        String str;
        int idx;

        launcher = launcher(false, cwd, "info");
        launcher.env("LC_ALL", "C");
        str = launcher.exec();
        idx = str.indexOf("URL:");
        return str.substring(idx, str.indexOf("\n", str.indexOf("URL:")));
    }

    public String ls(FileNode cwd, String url) throws Failure {
        return launcher(false, cwd, "ls", url).exec();
    }

    //--

    private Launcher interactive(FileNode cwd, String... args) {
        return launcher(true, cwd, args);
    }

    private Launcher launcher(boolean interactive, FileNode cwd, String... args) {
        Launcher launcher;

        launcher = new Launcher(cwd, "svn");
        if (!interactive) {
            launcher.arg("--non-interactive");
            launcher.arg("--trust-server-cert"); // needs svn >= 1.6
        }
        if (username != null) {
            launcher.arg("--no-auth-cache");
            launcher.arg("--username", username);
            launcher.arg("--password", password);
        }
        launcher.arg(args);
        return launcher;
    }
}

