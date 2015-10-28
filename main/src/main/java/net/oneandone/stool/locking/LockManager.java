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
package net.oneandone.stool.locking;

import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.channels.FileLock;

/** Thread-save and save against concurrent processes */
public class LockManager {
    public static LockManager create(FileNode file) {
        return new LockManager(file, new Process(pid(), ""));
    }

    public static int pid() {
        String str;
        int idx;

        // see http://blog.igorminar.com/2007/03/how-java-application-can-discover-its.html?m=1
        str = ManagementFactory.getRuntimeMXBean().getName();
        idx = str.indexOf('@');
        if (idx == -1) {
            throw new IllegalStateException("cannot guess pid from " + str);
        }
        return Integer.parseInt(str.substring(0, idx));
    }

    private final FileNode file;
    private final Process self;

    public LockManager(FileNode file, Process self) {
        this.file = file;
        this.self = self;
    }

    public Lock acquire(String name, Console console, Mode mode) throws IOException {
        switch (mode) {
            case NONE:
                break;
            case SHARED:
                await(name, false, console);
                break;
            case EXCLUSIVE:
                await(name, true, console);
                break;
            default:
                throw new IllegalStateException(mode.toString());
        }
        return new Lock(this, name, mode);
    }

    //--

    public void await(String name, boolean exclusive, Console console) throws IOException {
        int seconds;

        try {
            seconds = 0;
            while (!awaitStep(name, exclusive)) {
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

    private boolean awaitStep(String name, boolean exclusive) throws IOException {
        Store lf;

        try (RandomAccessFile raf = new RandomAccessFile(file.toPath().toFile(), "rw");
             FileLock lock = raf.getChannel().lock()) {
            lf = Store.load(raf);
            if (!lf.tryLock(name, exclusive, self)) {
                return false;
            }
            lf.save(raf);
            return true;
        }

    }

    public void release(String name, Mode mode) throws IOException {
        boolean exclusive;

        switch (mode) {
            case NONE:
                return;
            case SHARED:
                exclusive = false;
                break;
            case EXCLUSIVE:
                exclusive = true;
                break;
            default:
                throw new IllegalStateException(mode.toString());
        }
        Store lf;

        try (RandomAccessFile raf = new RandomAccessFile(file.toPath().toFile(), "rw");
             FileLock lock = raf.getChannel().lock()) {
            lf = Store.load(raf);
            lf.release(name, exclusive, self);
            lf.save(raf);
        }
    }

    public synchronized boolean empty() throws IOException {
        Store lf;

        try (RandomAccessFile raf = new RandomAccessFile(file.toPath().toFile(), "rw");
             FileLock lock = raf.getChannel().lock()) {
            lf = Store.load(raf);
            return lf.isEmpty();
        }
    }
}
