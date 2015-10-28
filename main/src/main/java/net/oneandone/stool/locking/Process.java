package net.oneandone.stool.locking;

import java.util.ArrayList;
import java.util.List;

/** Process with queues that contain it. Internal class. */
public class Process {
    public final int id;
    public final String comment;

    /** queues referencing this process */
    public final List<Queue> queues;

    public static Process parse(String line) {
        int idx;

        idx = line.indexOf(' ');
        if (idx == -1) {
            throw new IllegalArgumentException(line);
        }
        return new Process(Integer.parseInt(line.substring(0, idx)), line.substring(idx + 1));
    }

    public Process(int id, String comment) {
        this.id = id;
        this.comment = comment;
        this.queues = new ArrayList<>();
    }

    public Queue lookup(String name) {
        for (Queue queue : queues) {
            if (queue.is(name)) {
                return queue;
            }
        }
        return null;
    }

    public void add(Queue queue) {
        if (queues.contains(queue)) {
            throw new IllegalArgumentException(queue.lock);
        }
        queues.add(queue);
    }

    public void addOpt(Queue queue) {
        if (!queues.contains(queue)) {
            queues.add(queue);
        }
    }

    public void remove(Queue queue) {
        if (!queues.remove(queue)) {
            throw new IllegalArgumentException(queue.lock);
        }
    }

    public String toLine() {
        return id + ' ' + comment;
    }
}
