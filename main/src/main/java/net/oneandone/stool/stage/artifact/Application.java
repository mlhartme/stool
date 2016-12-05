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

import com.google.gson.Gson;
import net.oneandone.inline.Console;
import net.oneandone.stool.users.Users;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Reader;

public class Application {
    private final Gson gson;
    private final String name;
    public final Locator location;
    private final FileNode stageDirectory;
    private final Console console;

    private final WarFile backup;
    public final WarFile current;
    private final WarFile future;

    public Application(Gson gson, String name, Locator location, FileNode stageDirectory, Console console) {
        this.gson = gson;
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
        refresh().mkdir();
    }

    private FileNode refresh() {
        return stageDirectory.join(".refresh");
    }

    public FileNode base() {
        return stageDirectory.join(name);
    }

    public boolean refreshFuture(Session session, FileNode backstage) throws IOException {
        WarFile candidate;
        Changes changes;

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
        try {
            changes = changes(backstage, session.users);
        } catch (IOException e) {
            // TODO
            session.reportException("application.changes", e);
            changes = new Changes();
        }
        session.console.verbose.println("Update for " + null + " prepared.");
        for (Change change : changes) {
            console.info.print(change.getUser());
            console.info.print(" : ");
            console.info.println(change.getMessage());
        }
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

    private Changes changes(FileNode backstage, Users users) throws IOException {
        String svnurl;
        FileNode file;
        Changes changes;

        if (!future.exists() || !current.exists()) {
            return new Changes();
        }
        file = backstage.join("changes").join(future.file().md5() + ".changes");
        if (file.exists()) {
            try (Reader src = file.newReader()) {
                return gson.fromJson(src, Changes.class);
            }
        }
        svnurl = location.svnurl();
        if (svnurl == null) {
            return new Changes();
        }

        if (svnurl.contains("tags")) {
            changes = new XMLChangeCollector(current, future).collect();
        } else {
            changes = SCMChangeCollector.run(current, future, users, svnurl);
        }
        FileNode directory = file.getParent();
        directory.mkdirOpt();
        file.writeString(gson.toJson(changes));
        return changes;
    }

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
