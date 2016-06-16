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
import net.oneandone.sushi.cli.Command;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class SessionCommand implements Command {
    protected final Console console;
    protected final World world;
    protected final Session session;
    private final Mode globalLock;
    private final List<String> extraLines;

    public SessionCommand(Session session, Mode globalLock) {
        this.console = session.console;
        this.world = console.world;
        this.session = session;
        this.globalLock = globalLock;
        this.extraLines = new ArrayList<>();
    }

    @Option("nolock")
    protected boolean noLock;

    @Override
    public void invoke() throws Exception {
        String wasSelected;

        wasSelected = session.getSelectedStageName();
        try (Lock lock = createLock("ports", globalLock)) {
            doInvoke();
        }
        if (wasSelected == null) {
            if (session.getSelectedStageName() != null) {
                openShell();
            }
        } else {
            if (session.getSelectedStageName() == null) {
                exitShell();
            }
        }
        session.shellFileUpdate(extraLines);
    }

    public abstract void doInvoke() throws Exception;

    private void openShell() {
        addExtraLines("bash -rcfile " + session.environment.stoolBin(world).join("bash.rc").getAbsolute());
    }

    private void exitShell() {
        addExtraLines("exit");
    }

    private void addExtraLines(String line) {
        extraLines.add(line);
    }

    protected Lock createLock(String lock, Mode mode) throws IOException {
        return session.lockManager.acquire(lock, console, noLock ? Mode.NONE : mode);
    }

    public boolean inShell() {
        return session.getSelectedStageName() != null;
    }

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
}
