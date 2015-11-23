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
public class LockManager implements Runnable {
    public static LockManager create(FileNode file, String comment, int timeout) {
        return new LockManager(file, new Process(pid(), comment), timeout);
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

    private final List<Lock> active;
    private final FileNode file;
    private final Process self;
    /** seconds */
    private final int timeout;

    public LockManager(FileNode file, Process self, int timeout) {
        this.active = new ArrayList<>();
        this.file = file;
        this.self = self;
        this.timeout = timeout;
        Runtime.getRuntime().addShutdownHook(new Thread(this));
    }

    public Lock acquire(String name, Console console, Mode mode) throws IOException {
        Lock result;

        switch (mode) {
            case NONE:
                break;
            case SHARED:
                awaitLock(name, false, console);
                break;
            case EXCLUSIVE:
                awaitLock(name, true, console);
                break;
            default:
                throw new IllegalStateException(mode.toString());
        }
        result = new Lock(this, name, mode);
        active.add(result);
        return result;
    }

    /** for testing */
    public Lock acquireOpt(String name, Console console, Mode mode) throws IOException {
        try {
            return acquire(name, console, mode);
        } catch (LockException e) {
            return null;
        }
    }


    private void awaitLock(String name, boolean exclusive, Console console) throws IOException {
        Queue problem;
        int seconds;

        seconds = 0;
        while (true) {
            try {
                problem = tryLock(name, exclusive);
            } catch (IOException e) {
                throw new IOException("cannot acquire lock '" + name + "': " + e.getMessage(), e);
            }
            if (problem == null) {
                return;
            }
            if (seconds > timeout) {
                throw new LockException(name, problem);
            }
            if (seconds % 10 == 0) {
                console.info.println("trying to acquire lock '" + name + "'");
            }
            seconds++;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // continue
            }
        }
    }

    /** @return null or problematic queue otherwise */
    private Queue tryLock(String name, boolean exclusive) throws IOException {
        Store lf;
        Queue problem;

        try (RandomAccessFile raf = new RandomAccessFile(file.toPath().toFile(), "rw");
             FileLock lock = raf.getChannel().lock()) {
            lf = Store.load(raf);
            problem = lf.tryLock(name, exclusive, self);
            if (problem != null) {
                return problem;
            }
            lf.save(raf);
            return null;
        }

    }

    public void release(Lock lock) throws IOException {
        boolean exclusive;

        active.remove(lock);
        switch (lock.mode) {
            case NONE:
                return;
            case SHARED:
                exclusive = false;
                break;
            case EXCLUSIVE:
                exclusive = true;
                break;
            default:
                throw new IllegalStateException(lock.mode.toString());
        }
        Store lf;

        try (RandomAccessFile raf = new RandomAccessFile(file.toPath().toFile(), "rw");
             FileLock l = raf.getChannel().lock()) {
            lf = Store.load(raf);
            lf.release(lock.name, exclusive, self);
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

    public synchronized boolean hasExclusiveLocks(String ... locks) throws IOException {
        Store lf;
        Queue queue;

        try (RandomAccessFile raf = new RandomAccessFile(file.toPath().toFile(), "rw");
             FileLock l = raf.getChannel().lock()) {
            lf = Store.load(raf);
            for (String lock : locks) {
                queue = lf.lookup(lock);
                if (queue != null) {
                    if (queue.exclusiveCount > 0) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @Override
    public void run() {
        Lock lock;

        while (!active.isEmpty()) {
            lock = active.get(0);
            System.err.println("shutdown: unlocking " + lock.name);
            try {
                lock.close(); // does not need sushi
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
