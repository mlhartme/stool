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
package net.oneandone.stool;

import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.setup.Update;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Lock;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Command;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.fs.GetLastModifiedException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.List;

public abstract class SessionCommand implements Command {

    protected final Console console;
    protected final World world;
    protected final Session session;

    public SessionCommand(Session session) {
        this.console = session.console;
        this.world = console.world;
        this.session = session;
    }

    @Option("nolock")
    protected boolean noLock;


    @Option("updateCheck")
    private boolean updateCheck;

    // override if you don't want locking
    protected Lock lock() {
        return session.lock();
    }

    @Override
    public void invoke() throws Exception {
        Lock lock;

        updateCheck();
        lock = noLock ? null : lock();
        if (lock != null) {
            lock.aquire(getClass().getSimpleName().toLowerCase(), console);
        }

        try {
            doInvoke();
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
        session.invocationFileUpdate();
    }

    public abstract void doInvoke() throws Exception;

    protected void checkCommitted(Stage stage) throws IOException {
        FileNode directory;
        String str;

        directory = stage.getDirectory();
        if (!directory.join(".svn").isDirectory()) {
            return; // artifact stage
        }
        str = session.subversion().status(directory);
        if (isModified(str)) {
            message(Strings.indent(str, "  "));
            throw new IOException(directory + ": checkout has modifications - aborted.\n You may run with -force");
        }
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

    //--

    protected void run(Launcher l, Node output) throws IOException {
        message(l, output instanceof FileNode ? " > " + output : "");
        runQuiet(l, output);
    }

    protected void runQuiet(Launcher l, Node output) throws IOException {
        try (Writer out = output.createWriter()) {
            l.exec(out);
        }
    }

    protected void header(String h) {
        console.info.println("[" + h + "]");
    }

    protected void message(Launcher l, String suffix) {
        message(Separator.SPACE.join(l.getBuilder().command()) + suffix);
    }

    protected void message(String msg) {
        console.info.println(Strings.indent(msg, "  "));
    }

    protected void newline() {
        console.info.println();
    }

    //--

    private static final long MILLIS_PER_DAY = 1000 * 60 * 60 * 24;

    public void updateCheck() throws URISyntaxException, IOException {
        List<FileNode> updates;
        FileNode checked;

        checked = session.home.join("downloads/.update.checked");
        if (updateCheck || ((session.configuration.updateInterval > 0) && updateCheckPending(checked))) {
            updates = Update.check(net.oneandone.stool.setup.Main.versionObject(), session.home);
            for (FileNode file : updates) {
                console.info.println("UPDATE: Found a new Stool version.");
                console.info.println("UPDATE: See https://github.com/mlhartme/stool/releases for details");
                console.info.println("UPDATE: Install this update by running " + file);
            }
            if (!updates.isEmpty()) {
                console.info.println("UPDATE: (you can disable this up-to-date check in " + StoolConfiguration.configurationFile(session.home) + ", set 'updateInterval' to 0)");
            }
            checked.writeBytes();
            Files.stoolFile(checked);
        }
    }

    private boolean updateCheckPending(FileNode marker) throws GetLastModifiedException {
        return !marker.exists() || ((System.currentTimeMillis() - marker.getLastModified()) > session.configuration.updateInterval * MILLIS_PER_DAY);
    }
}
