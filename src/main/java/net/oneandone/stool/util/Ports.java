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

        session = stage.session;
        result = new Ports();
        if (stage.isOverview()) {
            result.add(session.stoolConfiguration.portPrefixOverview);
        } else {
            existing = Ports.load(stage.wrapper);
            used = null;
            for (String host : stage.selectedHosts().keySet()) {
                if (result.size() < existing.size()) {
                    result.add(existing, result.size());
                } else {
                    if (used == null) {
                        used = Ports.used(session.getWrappers());
                    }
                    result.add(used.notContained(host, session.stoolConfiguration.portPrefixFirst, session.stoolConfiguration.portPrefixLast).prefix);
                }
            }
        }
        // also save for overview stage - to have the ports read e.g. for status
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

    private final List<Data> list;

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

    public Data get(int idx) {
        return list.get(idx);
    }

    public void add(Ports ports, int idx) {
        list.add(ports.list.get(idx));
    }

    // TODO
    public void add(int prefix) {
        list.add(new Data(prefix));
    }

    public void save(FileNode wrapper) throws IOException {
        List<String> lines;

        lines = new ArrayList<>();
        for (Data data : list) {
            lines.add(Integer.toString(data.prefix));
        }
        file(wrapper).writeLines(lines);
    }

    public void append(FileNode wrapper) throws IOException {
        FileNode file;

        file = file(wrapper);
        if (file.isFile()) {
            for (String line : file.readLines()) {
                list.add(new Data(Integer.parseInt(line.trim())));
            }
        }
    }

    public void checkFree() throws IOException {
        for (Data data : list) {
            data.checkFree();
        }
    }

    public boolean contains(Data data) {
        return list.contains(data);
    }

    public Data notContained(String host, int first, int last) throws IOException {
        return notContained(host, new Data(first), new Data(last));
    }

    public Data notContained(String host, Data first, Data last) throws IOException {
        return notContained(Data.forName(host, first, last), first, last);
    }

    public Data notContained(Data start, Data first, Data last) throws IOException {
        Data current;

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

    private static class Data {
        public static Data forName(String name, Data first, Data last) {
            return new Data((Math.abs(name.hashCode()) % (last.prefix - first.prefix + 1)) + first.prefix);
        }

        // TODO: private
        public final int prefix;

        public Data(int prefix) {
            this.prefix = prefix;
        }

        public Data next() {
            return new Data(prefix + 1);
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
            if (obj instanceof Data) {
                return prefix == ((Data) obj).prefix;
            }
            return false;
        }

        public boolean within(Data first, Data last) {
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
    }
}
