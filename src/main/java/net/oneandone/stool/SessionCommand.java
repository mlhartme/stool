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

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Lock;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;

public abstract class SessionCommand extends BaseCommand {

    protected final Session session;

    @Option("nolock")
    protected boolean noLock;


    public SessionCommand(Session session) {
        super(session.console);
        this.session = session;
    }

    // override if you don't want locking
    protected Lock lock() {
        return session.lock();
    }

    @Override
    public void invoke() throws Exception {
        Lock lock;

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
                    // needed for pws workspace svn:externals  -> ok
                } else {
                    return true;
                }
            }
        }
        return false;
    }
}
