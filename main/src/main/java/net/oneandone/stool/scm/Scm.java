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
import net.oneandone.stool.util.Credentials;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;

import java.io.IOException;
import java.io.Writer;

public abstract class Scm {
    public static String checkoutUrl(FileNode dir) throws IOException {
        String result;

        result = checkoutUrlOpt(dir);
        if (result == null) {
            throw new IOException("not a checkout: " + dir);
        }
        return result;
    }

    /** Caution, does not work for nested directories */
    public static String checkoutUrlOpt(FileNode dir) throws Failure {
        String result;

        result = Subversion.svnCheckoutUrlOpt(dir);
        if (result != null) {
            return result;
        }
        result = Git.gitCheckoutUrlOpt(dir);
        if (result != null) {
            return result;
        }
        return null;
    }

    public abstract void checkout(String url, FileNode dir, Writer dest) throws Failure;
    public abstract boolean isCommitted(Stage stage) throws IOException;

    public static Scm forUrl(String url, Credentials svnCredentials) {
        if (url.startsWith("https:")) {
            return new Subversion(svnCredentials);
        } else {
            return new Git();
        }
    }
}

