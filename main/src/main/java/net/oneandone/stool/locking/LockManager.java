/*
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

import net.oneandone.inline.Console;
import net.oneandone.stool.util.Processes;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Pid;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.List;

/**
 * High-level management for the locks file. Factory for locks.
 * Thread-save and save against concurrent processes.
 */
public class LockManager extends Thread implements AutoCloseable {
    public static LockManager create(FileNode file, String comment, int timeout) {
        return new LockManager(file, new Process(Pid.pid(), comment), timeout);
    }

    private final List<Lock> active;
    private final FileNode file;
    private final Process self;
    /** seconds */
    private final int timeout;

    public LockManager(FileNode file, Process self, int timeout) {
        super("LockManager Shutdown Hook");
        this.active = new ArrayList<>();
        this.file = file;
        this.self = self;
        this.timeout = timeout;
        Runtime.getRuntime().addShutdownHook(this);
    }

    public synchronized Lock acquire(String name, Console console, Mode mode) throws IOException {
        Lock result;

        awaitLock(name, mode, console);
        result = new Lock(this, name, mode);
        active.add(result);
        return result;
    }

    /** for testing */
    public synchronized Lock acquireOpt(String name, Console console, Mode mode) throws IOException {
        try {
            return acquire(name, console, mode);
        } catch (LockException e) {
            return null;
        }
    }


    private void awaitLock(String name, Mode mode, Console console) throws IOException {
        Queue problem;
        int seconds;

        seconds = 0;
        while (true) {
            try {
                problem = tryLock(name, mode);
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
    private Queue tryLock(String name, Mode mode) throws IOException {
        Store lf;
        Queue problem;

        try (RandomAccessFile raf = new RandomAccessFile(file.toPath().toFile(), "rw"); FileLock lock = raf.getChannel().lock()) {
            lf = Store.load(raf);
            problem = lf.tryLock(name, mode, self);
            if (problem != null) {
                return problem;
            }
            lf.save(raf);
            return null;
        }

    }

    public synchronized void release(Lock lock) throws IOException {
        Store lf;

        active.remove(lock);
        try (RandomAccessFile raf = new RandomAccessFile(file.toPath().toFile(), "rw"); FileLock l = raf.getChannel().lock()) {
            lf = Store.load(raf);
            lf.release(lock.name, lock.mode, self);
            lf.save(raf);
        }
    }

    /** @return list of stale processes */
    public synchronized List<Integer> validate(Processes running, boolean repair) throws IOException {
        Store lf;
        List<Integer> stale;

        stale = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(file.toPath().toFile(), "rw"); FileLock lock = raf.getChannel().lock()) {
            lf = Store.load(raf);
            for (Process process : lf.processes()) {
                if (!running.hasPid(process.id)) {
                    stale.add(process.id);
                    if (repair) {
                        lf.releaseAll(process);
                    }
                }
            }
            if (repair) {
                lf.save(raf);
            }
        }
        return stale;
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

    /** close explicitly on LockManager object */
    public void close() {
        Runtime.getRuntime().removeShutdownHook(this);
        removeActive();
    }

    /** close implicitly during shutdown hook */
    @Override
    public void run() {
        removeActive();
    }

    private synchronized void removeActive() {
        Lock lock;

        while (!active.isEmpty()) {
            // caution: do no invoke active.remove(0) - lock.close() will remove it from the list
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
