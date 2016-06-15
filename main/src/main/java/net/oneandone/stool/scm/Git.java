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
package net.oneandone.stool.scm;

import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;

import java.io.IOException;
import java.io.Writer;

public class Git extends Scm {
    public static String gitCheckoutUrlOpt(FileNode dir) throws Failure {
        if (dir.join(".git").isDirectory()) {
            return gitCheckoutUrl(dir);
        } else {
            return null;
        }
    }

    private static String gitCheckoutUrl(FileNode checkout) throws Failure {
        return git(checkout, "config", "--get", "remote.origin.url").exec().trim();
    }

    private static Launcher git(FileNode cwd, String... args) {
        Launcher launcher;

        launcher = new Launcher(cwd, "git");
        launcher.arg(args);
        return launcher;
    }

    //--

    public Git() {
    }

    @Override
    public void checkout(String url, FileNode dir, Writer dest) throws Failure {
        git(dir.getParent(), "clone", url, dir.getName()).exec(dest);
    }

    @Override
    public boolean isCommitted(Stage stage) throws IOException {
        FileNode checkout;

        checkout = stage.getDirectory();
        try {
            git(checkout, "diff", "--quiet").execNoOutput();
        } catch (Failure e) {
            return false;
        }
        try {
            git(checkout, "diff", "--cached", "--quiet").execNoOutput();
        } catch (Failure e) {
            return false;
        }

        //  TODO: other branches
        try {
            git(checkout, "diff", "@{u}..HEAD", "--quiet").execNoOutput();
        } catch (Failure e) {
            return false;
        }
        return true;
    }
}

