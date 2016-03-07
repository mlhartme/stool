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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/** Persistent representation of locks. Internal class. */
public class Store {
    private static final char PROCESS = '@';
    private static final char EXCLUSIVE = '!';
    private static final char SHARED = '.';

    public static Store load(RandomAccessFile raf) throws IOException {
        Store result;
        String line;
        Process last;
        Queue queue;
        char marker;
        boolean exclusive;

        result = new Store();
        raf.seek(0);
        last = null;
        for (line = raf.readLine(); line != null; line = raf.readLine()) {
            if (line.isEmpty()) {
                throw new IllegalStateException();
            }
            marker = line.charAt(0);
            if (marker == PROCESS) {
                last = Process.parse(line.substring(1));
            } else {
                switch (marker) {
                    case SHARED:
                        exclusive = false;
                        break;
                    case EXCLUSIVE:
                        exclusive = true;
                        break;
                    default:
                        throw new IllegalStateException("unexpected marker: " + marker);
                }
                if (last == null) {
                    throw new IllegalStateException();
                }
                queue = result.getOrCreate(line.substring(1));
                if (!queue.tryLock(exclusive, last)) {
                    throw new IllegalStateException();
                }
            }
        }
        return result;
    }

    private final List<Queue> queues;

    public Store() {
        this.queues = new ArrayList<>();
    }

    public boolean isEmpty() {
        return queues.isEmpty();
    }

    public Queue lookup(String lock) {
        for (Queue queue : queues) {
            if (queue.is(lock)) {
                return queue;
            }
        }
        return null;
    }

    /** @return null for success, problematic queue otherwise */
    public Queue tryLock(String lock, boolean exclusive, Process processExtern) {
        Process process;
        Queue queue;

        process = intern(processExtern);
        queue = lookup(lock);
        if (queue == null) {
            queue = new Queue(lock);
            if (!queue.tryLock(exclusive, process)) {
                throw new IllegalStateException();
            }
            queues.add(queue);
        } else {
            if (!queue.tryLock(exclusive, process)) {
                return queue;
            }
        }
        return null;
    }

    public void release(String lock, boolean exclusive, Process processExtern) {
        Process process;
        Queue queue;

        process = intern(processExtern);
        queue = lookup(lock);
        if (queue == null) {
            throw new IllegalStateException(lock);
        }
        queue.release(exclusive, process);
    }

    public void releaseAll(Process processExtern) {
        Process process;

        process = intern(processExtern);
        for (Queue queue : queues) {
            queue.releaseAll(process);
        }
    }

    private Process intern(Process extern) {
        for (Process intern : processes()) {
            if (intern.id == extern.id) {
                return intern;
            }
        }
        return extern;
    }

    public void save(RandomAccessFile raf) throws IOException {
        List<Queue> queues;

        raf.seek(0);
        raf.setLength(0);
        for (Process process : processes()) {
            queues = queuesWithProcess(process);
            if (!queues.isEmpty()) {
                line(raf, PROCESS, process.toLine());
                for (Queue queue : queues) {
                    if (process == queue.exclusiveProcess) {
                        for (int count = queue.exclusiveCount; count > 0; count--) {
                            line(raf, EXCLUSIVE, queue.lock);
                        }
                    }
                    for (Process e : queue.shared) {
                        if (process == e) {
                            line(raf, SHARED, queue.lock);
                        }
                    }
                }
            }
        }
    }

    public List<Process> processes() {
        List<Process> result;

        result = new ArrayList<>();
        for (Queue queue : queues) {
            processes(queue, result);
        }
        return result;
    }

    private static void processes(Queue queue, List<Process> result) {
        if (queue.exclusiveCount > 0) {
            addOpt(result, queue.exclusiveProcess);
        }
        for (Process process : queue.shared) {
            addOpt(result, process);
        }
    }
    private static void addOpt(List<Process> result, Process process) {
        if (!result.contains(process)) {
            result.add(process);
        }
    }

    private List<Queue> queuesWithProcess(Process process) {
        List<Queue> result;

        result = new ArrayList<>();
        for (Queue queue : queues) {
            if (queue.contains(process)) {
                if (!result.contains(queue)) {
                    result.add(queue);
                }
            }
        }
        return result;
    }

    private static void line(RandomAccessFile raf, char marker, String line) throws IOException {
        raf.writeByte(marker);
        raf.writeBytes(line);
        raf.writeByte('\n');
    }

    private Queue getOrCreate(String name) {
        Queue result;

        for (Queue queue : queues) {
            if (queue.is(name)) {
                return queue;
            }
        }
        result = new Queue(name);
        queues.add(result);
        return result;
    }
}
