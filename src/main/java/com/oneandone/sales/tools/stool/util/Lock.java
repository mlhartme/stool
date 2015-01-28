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
package com.oneandone.sales.tools.stool.util;

import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.OnShutdown;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.File;
import java.io.IOException;

public class Lock {
    private static final String DELIM_CHAR = ";";

    private final String user;
    private final FileNode lock;

    public Lock(String user, FileNode lock) {
        this.user = user;
        this.lock = lock;
    }

    public void aquire(String opInProgress, Console console) throws IOException, InterruptedException {
        String[] userAndOpInProgress;
        File lockfile = lock.toPath().toFile();
        int seconds = 0;

        // as long as file exists we have to wait
        try {
            while (!lockfile.createNewFile()) {
                if (seconds > 60) {
                    throw new IOException("cannot acquire lock " + lock);
                }
                userAndOpInProgress = getLockDescription();
                if (seconds % 10 == 0) {
                    console.info.println("Waiting for " + userAndOpInProgress[0]
                      + " (who runs 'stool " + userAndOpInProgress[1] + "'): " + seconds + "s" + " (Ctrl-C to abort)");
                }
                seconds++;
                Thread.sleep(1000);
            }
        } catch (IOException e) {
            throw new IOException("cannot create lock " + lock + ": " + e.getMessage(), e);
        }

        // register first, because a full disk causes writeLockDescription to fail
        OnShutdown.get().deleteAtExit(lock);
        writeLockDescription(user + DELIM_CHAR + opInProgress);
    }

    public void release() throws IOException {
        if (lock.exists()) {
            lock.deleteFile();
            OnShutdown.get().dontDeleteAtExit(lock);
        }
    }

    //--

    private String[] getLockDescription() {
        try {
            return lock.readString().split(DELIM_CHAR);
        } catch (Exception e) {
            // the lock file might have any content, it might even have been removed by a concurrent user.
            return new String[]{"unknownuser", "unknown reason"};
        }
    }

    private void writeLockDescription(String description) throws IOException {
        lock.writeString(description);
        Files.stoolFile(lock);
    }
}
