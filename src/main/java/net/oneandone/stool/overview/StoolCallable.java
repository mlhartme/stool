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
package net.oneandone.stool.overview;

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Session;
import net.oneandone.stool.util.Subversion;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.Callable;

public class StoolCallable implements Callable<Failure> {
    public static StoolCallable create(String id, FileNode logs, Stage stage, String command, String ... options) throws IOException {
        Session session;

        session = stage.session;
        return new StoolCallable(session.bin("stool-raw.sh"), command, options, stage, id, logs, session.configuration.shared, stage.owner());
    }

    private final FileNode stoolRaw;
    private final String command;
    private final String[] options;
    private final String id;
    private final Stage stage;
    private final FileNode logDir;
    private final boolean su;
    private final String runAs;

    public StoolCallable(FileNode stoolRaw, String command, String[] options, Stage stage, String id, FileNode logDir, boolean su, String runAs) {
        this.stoolRaw = stoolRaw;
        this.command = command;
        this.options = options;
        this.id = id;
        this.stage = stage;
        this.logDir = logDir;
        this.su = su;
        this.runAs = runAs;
    }

    @Override
    public Failure call() throws Exception {
        Launcher launcher;
        Failure failure;
        long time;
        BuildStats buildStats;
        Subversion subversion;
        FileNode running;

        failure = null;
        time = System.currentTimeMillis();
        launcher = new Launcher(logDir);
        if (su) {
            launcher.arg("sudo", "-u", runAs);
        }
        launcher.arg(stoolRaw.getAbsolute());
        subversion = stage.session.subversion();
        if (subversion.username != null) {
            launcher.arg("-svnuser", subversion.username);
            launcher.arg("-svnpassword", subversion.password);
        }
        launcher.arg(command, "-stage", stage.getName());
        launcher.arg(options);
        try (PrintWriter writer = new PrintWriter(logDir.join(id + ".log").createWriter())) {
            writer.println(hide(launcher.toString(), subversion.password));
            running = logDir.join(id + ".running").mkfile();
            try {
                launcher.exec(writer);
            } catch (Failure e) {
                failure = e;
                e.printStackTrace(writer);
            } finally {
                running.deleteFile();
            }
            time = System.currentTimeMillis() - time;
            writer.println((failure == null ? "OK" : "FAILED") + " (ms= " + time + ")");
        }
        buildStats = BuildStats.load(logDir, stage);
        buildStats.add(command, time);
        buildStats.save();
        return failure;
    }

    private static String hide(String str, String hide) {
        return hide == null ? str : str.replace(hide, "********");
    }
}
