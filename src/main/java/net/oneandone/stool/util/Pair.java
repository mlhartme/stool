package net.oneandone.stool.util;

import java.util.Objects;

/** Immutable pair of (possibly null) strings */
public class Pair {
    public final String left;
    public final String right;

    public Pair(String left, String right) {
        this.left = left;
        this.right = right;
    }

    public int hashCode() {
        return left == null ? right.hashCode() : left.hashCode();
    }

    public boolean equals(Object obj) {
        Pair p;

        if (obj instanceof Pair) {
            p = (Pair) obj;
            return Objects.equals(p.left, left) && Objects.equals(p.right, right);
        } else {
            return false;
        }
    }
}
