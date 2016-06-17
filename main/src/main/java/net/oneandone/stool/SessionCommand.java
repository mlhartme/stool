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

import net.oneandone.stool.locking.Lock;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.util.Session;
import net.oneandone.inline.Console;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public abstract class SessionCommand {
    protected final Console console;
    protected final World world;
    protected final Session session;
    private final Mode globalLock;

    public SessionCommand(Session session, Mode globalLock) {
        this.console = session.console;
        this.world = session.world;
        this.session = session;
        this.globalLock = globalLock;
    }

    public void run() throws Exception {
        String wasSelected;
        List<String> extraLines;

        wasSelected = session.getSelectedStageName();
        extraLines = new ArrayList<>();
        try (Lock lock = createLock("ports", globalLock)) {
            doInvoke();
        }
        if (wasSelected == null) {
            if (session.getSelectedStageName() != null) {
                extraLines.add("bash -rcfile " + session.environment.stoolBin(world).join("bash.rc").getAbsolute());
            }
        } else {
            if (session.getSelectedStageName() == null) {
                extraLines.add("exit");
            }
        }
        session.shellFileUpdate(extraLines);
    }

    public abstract void doInvoke() throws Exception;

    protected Lock createLock(String lock, Mode mode) throws IOException {
        return session.lockManager.acquire(lock, console, session.globals.nolock ? Mode.NONE : mode);
    }

    protected void run(Launcher l, Node output) throws IOException {
        message(l, output instanceof FileNode ? " > " + output : "");
        runQuiet(l, output);
    }

    protected void runQuiet(Launcher l, Node output) throws IOException {
        try (Writer out = output.newWriter()) {
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
}
