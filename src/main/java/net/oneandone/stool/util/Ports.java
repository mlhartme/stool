package net.oneandone.stool.util;

import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Ports {
    // TODO: ugly reference to stage ...
    public static Ports forStage(Stage stage) throws IOException {
        Session session;
        Ports existing;
        Ports result;
        Ports used;
        List<String> names;
        List<String> hosts;

        session = stage.session;
        result = new Ports();
        existing = Ports.load(stage.wrapper);
        used = null;
        names = new ArrayList<>();
        names.add(stage.getName() + "-stop");
        names.add(stage.getName() + "-jmx-debug");
        hosts = new ArrayList<>(stage.selectedHosts().keySet());
        Collections.sort(hosts); // guarantied ordering
        for (String host : hosts) {
            names.add(host);
        }
        for (String name : names) {
            if (result.evens.size() < existing.evens.size()) {
                result.evens.add(existing.evens.get(result.evens.size()));
            } else {
                if (used == null) {
                    used = Ports.used(session.getWrappers());
                }
                if (stage.isOverview()) {
                    if (hosts.size() != 1) {
                        throw new IllegalStateException();
                    }
                    result.evens.add(session.stoolConfiguration.portOverview);
                } else {
                    result.evens.add(used.notContained(name, session.stoolConfiguration.portFirst, session.stoolConfiguration.portLast));
                }
            }
        }
        result.save(stage.wrapper);
        return result;
    }

    public static Ports used(List<FileNode> wrappers) throws IOException {
        Ports result;

        result = new Ports();
        for (FileNode wrapper : wrappers) {
            result.evens.addAll(load(wrapper).evens);
        }
        return result;
    }

    public static Ports load(FileNode wrapper) throws IOException {
        Ports result;

        result = new Ports();
        result.append(wrapper);
        return result;
    }

    private static FileNode file(FileNode wrapper) {
        return wrapper.join("ports");
    }

    //--

    /** stores even ports nummers; its successor is also considered as allocated */
    private final List<Integer> evens;

    public Ports() {
        evens = new ArrayList<>();
    }

    public int hosts() {
        return evens.size() - 2;
    }

    public int stop() {
        return evens.get(0);
    }

    public int jmx() {
        return evens.get(1);
    }

    public int debug() {
        return evens.get(1) + 1;
    }

    public int http(int idx) {
        return evens.get(2 + idx);
    }

    public int https(int idx) {
        return evens.get(2 + idx) + 1;
    }

    //--

    private void save(FileNode wrapper) throws IOException {
        List<String> lines;

        lines = new ArrayList<>();
        for (Integer even : evens) {
            lines.add(even.toString());
        }
        file(wrapper).writeLines(lines);
    }

    private void append(FileNode wrapper) throws IOException {
        FileNode file;

        file = file(wrapper);
        if (file.isFile()) {
            for (String line : file.readLines()) {
                evens.add(Integer.parseInt(line.trim()));
            }
        }
    }

    private boolean contains(int even) {
        return evens.contains(even);
    }

    private int notContained(String host, int first, int last) throws IOException {
        return notContained(forName(host, first, last), first, last);
    }

    private int notContained(int start, int first, int last) throws IOException {
        int current;

        if ((start < first) || (start > last)) {
            throw new IllegalArgumentException("ports out of range: " + start);
        }
        if (start % 2 != 0) {
            throw new IllegalArgumentException("even port expected: " + start);
        }
        if (first % 2 != 0) {
            throw new IllegalArgumentException("even port expected: " + first);
        }
        if (last % 2 != 1) {
            throw new IllegalArgumentException("odd port expected: " + last);
        }
        current = start;
        do {
            if (!contains(current)) {
                // port prefix isn't used by another stage
                checkFree(current);
                return current;
            }
            if (current == last) {
                current = first;
            } else {
                current = current + 2;
            }
        } while (current != start);
        throw new IOException("cannot allocate port");
    }

    //

    /**
     * See http://stackoverflow.com/questions/434718/sockets-discover-port-availability-using-java
     */
    private static void checkFree(int port) throws IOException {
        boolean available;
        ServerSocket socket;

        socket = null;
        available = false;
        try {
            socket = new ServerSocket(port);
            available = true;
        } catch (IOException e) {
            // fall-through
        } finally {
            // Clean up
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    /* should not be thrown */
                    e.printStackTrace();
                }
            }
        }
        if (!available) {
            throw new IOException("port already in use: " + port);
        }
    }

    public static int forName(String name, int first, int last) {
        return ((Math.abs(name.hashCode()) % (last - first + 1)) + first) & 0xfffffffe;
    }
}
