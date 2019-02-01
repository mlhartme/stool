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
package net.oneandone.stool.stage.artifact;

import net.oneandone.inline.Console;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class Application {
    private final String name;
    public final Locator location;
    private final FileNode stageDirectory;
    private final Console console;

    private final WarFile backup;
    public final WarFile current;
    private final WarFile future;

    public Application(String name, Locator location, FileNode stageDirectory, Console console) {
        this.name = name;
        this.location = location;
        this.stageDirectory = stageDirectory;
        this.console = console;
        this.backup = new WarFile(refresh().join(name() + ".war.backup"));
        this.current = new WarFile(base().join("ROOT.war"));
        this.future = new WarFile(refresh().join(name() + ".war.next"));

    }

    public void populate() throws MkdirException {
        base().mkdir();
        refresh().mkdirOpt(); // opt because it's shared by all stages
    }

    private FileNode refresh() {
        return stageDirectory.join(".refresh");
    }

    public FileNode base() {
        return stageDirectory.join(name);
    }

    public boolean refreshFuture(Session session) throws IOException {
        WarFile candidate;

        candidate = location.resolve();
        if (candidate == null) {
            return false;
        }
        if (candidate.equals(current)) {
            return false;
        }
        if (candidate.equals(future)) {
            return false;
        }

        candidate.copyTo(future);
        session.console.verbose.println("Update for " + null + " prepared.");
        return true;
    }

    public boolean updateAvailable() {
        return future.exists();

    }

    public void update() throws IOException {
        backup();
        future.copyTo(current);
        console.verbose.println("Update for " + name + " executed.");
        current.file().getParent().join("ROOT").deleteTreeOpt();
    }

    public void restore() throws IOException {
        if (backup.exists()) {
            console.info.println("Restoring backup of  " + name);
            backup.copyTo(current);
            console.info.println("Restored.");
        } else {
            console.info.println("No backup available for " + name);
        }
    }

    //--

    private void backup() throws IOException {
        if (current.exists()) {
            current.file().copy(backup.file());
            console.info.println("Backup for " + name + " created.");
        }
    }

    //--

    private String name() {
        return name;
    }
}
