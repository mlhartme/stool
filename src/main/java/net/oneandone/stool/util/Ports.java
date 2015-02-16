package net.oneandone.stool.util;

import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

public class Ports {
    // TODO: ugly reference to stage ...
    public static Ports forStage(Stage stage) throws IOException {
        Session session;
        Ports existing;
        Ports result;
        Ports used;
        List<String> names;

        session = stage.session;
        result = new Ports();
        existing = Ports.load(stage.wrapper);
        used = null;
        names = new ArrayList<>();
        names.add(stage.getName() + "-stop");
        names.add(stage.getName() + "-jmx");
        names.add(stage.getName() + "-debug");
        for (String name : stage.selectedHosts().keySet()) {
            names.add(name + "-http");
            names.add(name + "-https");
        }
        for (String name : names) {
            if (result.list.size() < existing.list.size()) {
                result.list.add(existing.list.get(result.list.size()));
            } else {
                if (used == null) {
                    used = Ports.used(session.getWrappers());
                }
                result.list.add(used.notContained(name, session.stoolConfiguration.portFirst, session.stoolConfiguration.portLast));
            }
        }
        result.save(stage.wrapper);
        return result;
    }

    public static Ports used(List<FileNode> wrappers) throws IOException {
        Ports result;

        result = new Ports();
        for (FileNode wrapper : wrappers) {
            result.list.addAll(load(wrapper).list);
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

    private final List<Integer> list;

    public Ports() {
        list = new ArrayList<>();
    }

    public int hosts() {
        return (list.size() - 3) / 2;
    }

    public int stop() {
        return list.get(0);
    }

    public int jmx() {
        return list.get(1);
    }

    public int debug() {
        return list.get(2);
    }

    public int http(int idx) {
        return list.get(3 + idx * 2);
    }

    public int https(int idx) {
        return list.get(3 + idx * 2 + 1);
    }

    //--

    private void save(FileNode wrapper) throws IOException {
        List<String> lines;

        lines = new ArrayList<>();
        for (Integer port : list) {
            lines.add(port.toString());
        }
        file(wrapper).writeLines(lines);
    }

    private void append(FileNode wrapper) throws IOException {
        FileNode file;

        file = file(wrapper);
        if (file.isFile()) {
            for (String line : file.readLines()) {
                list.add(Integer.parseInt(line.trim()));
            }
        }
    }

    private boolean contains(int port) {
        return list.contains(port);
    }

    private int notContained(String host, int first, int last) throws IOException {
        return notContained(forName(host, first, last), first, last);
    }

    private int notContained(int start, int first, int last) throws IOException {
        int current;

        if ((start < first) || (start > last)) {
            throw new IllegalArgumentException("ports out of range: " + start);
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
                current = current + 1;;
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
        return (Math.abs(name.hashCode()) % (last - first + 1)) + first;
    }
}
