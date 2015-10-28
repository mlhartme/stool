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
import java.util.ArrayList;
import java.util.List;

/** Thread-save and save against concurrent processes */
public class LockManager {
    public static LockManager create(FileNode file) {
        return new LockManager(file, pid());
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
    private final int process;

    public LockManager(FileNode file, int process) {
        this.file = file;
        this.process = process;
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
            while (!modify(name, false, exclusive)) {
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
        if (!modify(name, true, exclusive)) {
            throw new IllegalStateException(name);
        }
    }

    public synchronized boolean empty() throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toPath().toFile(), "rw");
             FileLock lock = raf.getChannel().lock()) {
            for (LockEntry entry : load(raf)) {
                if (entry.toLine() != null) {
                    return false;
                }
            }
        }
        return true;
    }

    //--

    private synchronized boolean modify(String name, boolean release, boolean exclusive) throws IOException {
        List<LockEntry> all;

        if (name.indexOf(' ') != -1) {
            throw new IllegalArgumentException(name);
        }
        try (RandomAccessFile raf = new RandomAccessFile(file.toPath().toFile(), "rw");
             FileLock lock = raf.getChannel().lock()) {
            all = load(raf);
            for (LockEntry entry : all) {
                if (entry.is(name)) {
                    if (release) {
                        entry.release(exclusive, process);
                        save(raf, all);
                        return true;
                    } else {
                        if (entry.tryLock(exclusive, process)) {
                            save(raf, all);
                            return true;
                        }
                        return false;
                    }
                }
            }
            if (!release) {
                LockEntry entry;

                entry = new LockEntry(name);
                if (!entry.tryLock(exclusive, process)) {
                    throw new IllegalStateException();
                }
                all.add(entry);
                save(raf, all);
                return true;
            }
            return false;
        }
    }

    private static List<LockEntry> load(RandomAccessFile raf) throws IOException {
        List<LockEntry> result;
        String line;

        result = new ArrayList<>();
        raf.seek(0);
        for (line = raf.readLine(); line != null; line = raf.readLine()) {
            result.add(LockEntry.parse(line));
        }
        return result;
    }

    private static void save(RandomAccessFile raf, List<LockEntry> entries) throws IOException {
        String line;

        raf.seek(0);
        raf.setLength(0);
        for (LockEntry entry : entries) {
            line = entry.toLine();
            if (line != null) {
                raf.writeBytes(line);
                raf.writeByte('\n');
            }
        }
    }
}
