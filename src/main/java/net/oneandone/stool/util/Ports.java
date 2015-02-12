package net.oneandone.stool.util;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

public class Ports {
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

    private final List<PortData> list;

    public Ports() {
        list = new ArrayList<>();
    }

    public int size() {
        return list.size();
    }

    public int jmx() {
        return get(0).jmx();
    }

    public int stop() {
        return get(0).tomcatStop();
    }

    public int debug() {
        return get(0).debugPort();
    }

    public int http(int idx) {
        return list.get(idx).tomcatHttp();
    }

    public int https(int idx) {
        return list.get(idx).tomcatHttps();
    }

    public PortData get(int idx) {
        return list.get(idx);
    }

    public void add(Ports ports, int idx) {
        list.add(ports.list.get(idx));
    }

    // TODO
    public void add(PortData portData) {
        list.add(portData);
    }

    public void save(FileNode wrapper) throws IOException {
        List<String> lines;

        lines = new ArrayList<>();
        for (PortData portData : list) {
            lines.add(Integer.toString(portData.prefix));
        }
        file(wrapper).writeLines(lines);
    }

    public void append(FileNode wrapper) throws IOException {
        FileNode file;

        file = file(wrapper);
        if (file.isFile()) {
            for (String line : file.readLines()) {
                list.add(new PortData(Integer.parseInt(line.trim())));
            }
        }
    }

    public void checkFree() throws IOException {
        for (PortData portData : list) {
            portData.checkFree();
        }
    }

    public boolean contains(PortData portData) {
        return list.contains(portData);
    }

    public Ports.PortData notContained(String host, PortData first, PortData last) throws IOException {
        return notContained(Ports.PortData.forName(host, first, last), first, last);
    }

    public Ports.PortData notContained(Ports.PortData start, PortData first, PortData last) throws IOException {
        PortData current;

        if (!start.within(first, last)) {
            throw new IllegalArgumentException("ports out of range: " + start);
        }
        current = start;
        do {
            if (!contains(current)) {
                // port prefix isn't used by another stage
                current.checkFree();
                return current;
            }
            if (current.equals(last)) {
                current = first;
            } else {
                current = current.next();
            }
        } while (!current.equals(start));
        throw new IOException("cannot allocate ports");
    }



    //--

    public static class PortData {
        public static PortData forName(String name, PortData first, PortData last) {
            return new PortData((Math.abs(name.hashCode()) % (last.prefix - first.prefix + 1)) + first.prefix);
        }

        // TODO: private
        public final int prefix;

        public PortData(int prefix) {
            this.prefix = prefix;
        }

        public PortData next() {
            return new PortData(prefix + 1);
        }

        private int tomcatHttp() {
            return prefix * 10;
        }

        private int tomcatStop() {
            return prefix * 10 + 1;
        }

        private int tomcatHttps() {
            return prefix * 10 + 3;
        }

        private int debugPort() {
            return prefix * 10 + 5;
        }

        private int jmx() {
            return prefix * 10 + 6;
        }

        //--

        public String toString() {
            return Integer.toString(prefix);
        }

        public int hashCode() {
            return prefix;
        }

        public boolean equals(Object obj) {
            if (obj instanceof PortData) {
                return prefix == ((PortData) obj).prefix;
            }
            return false;
        }

        public boolean within(PortData first, PortData last) {
            return prefix >= first.prefix && prefix <= last.prefix;
        }

        //--
        /**
         * See http://stackoverflow.com/questions/434718/sockets-discover-port-availability-using-java
         */
        public void checkFree() throws IOException {
            boolean available;
            ServerSocket socket;

            // convert portPrefix (three digits) into a proper port (add fourth digit aka suffix)
            List<Integer> portsToCheck = new ArrayList<>();
            portsToCheck.add(jmx());
            portsToCheck.add(debugPort());
            portsToCheck.add(tomcatStop());
            portsToCheck.add(tomcatHttp());
            portsToCheck.add(tomcatHttps());

            for (int portNumber : portsToCheck) {
                socket = null;
                available = false;
                try {
                    socket = new ServerSocket(portNumber);
                    available = true;
                } catch (IOException e) {
                    break;
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
                    throw new IOException("portPrefix already in used: " + prefix);
                }
            }
        }

        // TODO: for stool config ...
        public static class PortsTypeAdapter extends TypeAdapter<PortData> {
            @Override
            public void write(JsonWriter out, PortData value) throws IOException {
                out.value(value.prefix);
            }

            @Override
            public PortData read(JsonReader in) throws IOException {
                return new PortData(in.nextInt());
            }
        }
    }
}
