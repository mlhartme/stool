package net.oneandone.stool.locking;

import java.io.IOException;

/**
 * Thrown if a lock cannot be acquired
 */
public class LockException extends IOException {
    public LockException(String lock, Queue problem) {
        super("cannot acquire lock " + lock + ": " + problem.problem());
    }
}
