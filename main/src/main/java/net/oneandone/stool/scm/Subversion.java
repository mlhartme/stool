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
package net.oneandone.stool.scm;

import net.oneandone.stool.stage.Project;
import net.oneandone.stool.util.Credentials;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.Writer;

public class Subversion extends Scm {
    /** Caution, does not work for nested directories */
    public static String svnCheckoutUrlOpt(FileNode dir) throws Failure {
        if (dir.join(".svn").isDirectory()) {
            return "svn:" + svnCheckoutUrl(dir);
        } else {
            return null;
        }
    }

    private static String svnCheckoutUrl(FileNode dir) throws Failure {
        Launcher launcher;
        String str;
        int idx;

        launcher = new Launcher(dir, "svn", "info");
        launcher.env("LC_ALL", "C");
        str = launcher.exec();
        idx = str.indexOf("URL:") + 4;
        return str.substring(idx, str.indexOf("\n", idx)).trim();
    }


    //--

    public final Credentials credentials;

    public Subversion(Credentials credentials) {
        super("svn @svnCredentials@ up");
        this.credentials = credentials;
    }

    @Override
    public void checkout(String url, FileNode dir, Writer dest) throws Failure {
        launcher(dir.getParent(), "co", Strings.removeLeft(url, "svn:"), dir.getName()).exec(dest);
    }

    @Override
    public boolean isCommitted(Project project) throws IOException {
        FileNode directory;
        String str;

        directory = project.getDirectory();
        if (!directory.join(".svn").isDirectory()) {
            return true; // artifact stage
        }
        str = status(directory);
        return !isModified(str);
    }

    private String status(FileNode cwd) throws Failure {
        Launcher launcher;

        launcher = launcher(cwd, "status");
        launcher.env("LC_ALL", "C");
        return launcher.exec();
    }

    private static boolean isModified(String lines) {
        for (String line : Separator.on("\n").split(lines)) {
            if (line.trim().length() > 0) {
                if (line.startsWith("X") || line.startsWith("Performing status on external item")) {
                    // needed for external references
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    private Launcher launcher(FileNode cwd, String... args) {
        Launcher launcher;

        launcher = new Launcher(cwd, "svn");
        launcher.arg(credentials.svnArguments());
        launcher.arg(args);
        return launcher;
    }
}

