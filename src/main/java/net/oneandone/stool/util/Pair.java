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
        if (obj instanceof Pair p) {
            return Objects.equals(p.left, left) && Objects.equals(p.right, right);
        } else {
            return false;
        }
    }

    public String encode() {
        return encode(left) + encode(right);
    }


    private static final int NIL_LEN = 9999;
    private static final String NIL = new String(new char[] { NIL_LEN });

    private static String encode(String str) {
        if (str == null) {
            return NIL;
        } else {
            if (str.length() >= NIL_LEN) {
                throw new IllegalArgumentException(str);
            }
            return ((char) str.length()) + str;
        }
    }

    public static Pair decode(String str) {
        int len;
        int rest;
        String left;
        String right;

        if (str.isEmpty()) {
            throw new IllegalArgumentException(str);
        }
        len = str.charAt(0);
        if (len == NIL_LEN) {
            left = null;
            rest = 1;
        } else {
            left = str.substring(1, 1 + len);
            rest = len + 1;
        }
        if (rest >= str.length()) {
            throw new IllegalArgumentException(str);
        }
        len = str.charAt(rest);
        if (len == NIL_LEN) {
            right = null;
        } else {
            right = str.substring(rest + 1, len + rest + 1);
        }
        return new Pair(left, right);
    }
}
