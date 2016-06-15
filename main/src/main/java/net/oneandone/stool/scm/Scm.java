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

import java.io.IOException;
import java.io.Writer;

public abstract class Scm {
    /** Caution, does not work for nested directories */
    public static String probeRootCheckoutUrl(FileNode dir) throws Failure {
        return Subversion.probeRootCheckoutUrl(dir);
    }

    public static String checkoutUrl(FileNode dir) throws Failure {
        return Subversion.checkoutUrl(dir);
    }

    public abstract void checkout(FileNode cwd, String url, String name, Writer dest) throws Failure;
    public abstract String status(FileNode cwd) throws Failure;
    public abstract boolean isCommitted(Stage stage) throws IOException;
}

