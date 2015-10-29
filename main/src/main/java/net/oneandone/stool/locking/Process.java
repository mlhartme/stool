package net.oneandone.stool.locking;

/** Process id with a comment. Internal class */
class Process {
    public final int id;
    public final String comment;

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
    }

    public String toLine() {
        return Integer.toString(id) + ' ' + comment;
    }

    public String toString() {
        return toLine();
    }
}
