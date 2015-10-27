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
package net.oneandone.stool.util;

import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;

public class Lock implements AutoCloseable {
    public enum Mode {
        NONE, SHARED, EXCLUSIVE
    }

    private static final OpenOption[] SHARED = { StandardOpenOption.READ };
    private static final OpenOption[] EXCLUSIVE = { StandardOpenOption.WRITE, StandardOpenOption.APPEND };

    public static Lock create(FileNode file, Console console, Mode mode) throws IOException {
        int seconds;
        FileChannel channel;
        FileLock lock;
        boolean shared;

        if (mode == Mode.NONE) {
            lock = null;
        } else {
            shared = mode == Mode.SHARED;
            seconds = 0;
            try {
                channel = FileChannel.open(file.toPath(), shared ? SHARED : EXCLUSIVE);
                while (true) {
                    lock = channel.tryLock(0, Long.MAX_VALUE, shared);
                    if (lock != null) {
                        break;
                    }
                    if (seconds > 10) {
                        throw new IOException("waiting for lock timed out");
                    }
                    if (seconds % 10 == 0) {
                        console.info.println("trying to lock " + file);
                    }
                    seconds++;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // continue
                    }
                }
            } catch (IOException e) {
                throw new IOException("cannot lock " + file + ": " + e.getMessage(), e);
            }
        }
        return new Lock(lock);

    }

    private FileLock lock;

    public Lock(FileLock lock) {
        this.lock = lock;
    }

    @Override
    public void close() throws Exception {
        if (lock != null) {
            lock.release();
            lock.channel().close();
        } else {
            // noop lock
        }
    }

    public String toString() {
        return lock == null ? "none" : lock.toString();
    }
}
