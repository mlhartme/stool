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
                continue;
            } else {
                marker = line.charAt(0);
                if (marker == PROCESS) {
                    last = Process.parse(line.substring(1));
                    result.processes.add(last);
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
                    queue.tryLock(exclusive, last);
                }
            }
        }
        return result;
    }

    private final List<Process> processes;
    private final List<Queue> queues;

    public Store() {
        this.processes = new ArrayList<>();
        this.queues = new ArrayList<>();
    }

    public boolean isEmpty() {
        return processes.isEmpty();
    }

    public Queue lookup(String lock) {
        for (Queue queue : queues) {
            if (queue.is(lock)) {
                return queue;
            }
        }
        return null;
    }

    public boolean tryLock(String lock, boolean exclusive, Process process) {
        Queue queue;

        queue = lookup(lock);
        if (queue == null) {
            queue = new Queue(lock);
            if (!queue.tryLock(exclusive, process)) {
                throw new IllegalStateException();
            }
        } else {
            if (!queue.tryLock(exclusive, process)) {
                return false;
            }
        }
        if (!queues.contains(queue)) {
            queues.add(queue);
        }
        if (!processes.contains(process)) {
            processes.add(process);
        }
        return true;
    }

    public void release(String lock, boolean exclusive, Process process) {
        Queue queue;

        queue = lookup(lock);
        if (queue == null) {
            throw new IllegalStateException(lock);
        }
        queue.release(exclusive, process);
    }

    public void save(RandomAccessFile raf) throws IOException {
        raf.seek(0);
        raf.setLength(0);
        for (Process process : processes) {
            if (!process.queues.isEmpty()) {
                raf.writeByte(PROCESS);
                raf.writeBytes(process.toLine());
                raf.writeByte('\n');
                for (Queue entry : process.queues) {
                    if (process == entry.exclusiveProcess) {
                        for (int count = entry.exclusiveCount; count > 0; count--) {
                            line(raf, EXCLUSIVE, entry.lock);
                        }
                    }
                    for (Process e : entry.shared) {
                        if (process == e) {
                            line(raf, SHARED, entry.lock);
                        }
                    }
                }
            }
        }
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
