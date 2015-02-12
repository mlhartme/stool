package net.oneandone.stool.util;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

public class PortsList {
    public static PortsList used(List<FileNode> wrappers) throws IOException {
        PortsList result;

        result = new PortsList();
        for (FileNode wrapper : wrappers) {
            result.list.addAll(load(wrapper).list);
        }
        return result;
    }

    public static PortsList load(FileNode wrapper) throws IOException {
        PortsList result;

        result = new PortsList();
        result.append(wrapper);
        return result;
    }

    private static FileNode file(FileNode wrapper) {
        return wrapper.join("ports");
    }

    //--

    private final List<Ports> list;

    public PortsList() {
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

    public Ports get(int idx) {
        return list.get(idx);
    }

    public void add(PortsList ports, int idx) {
        list.add(ports.list.get(idx));
    }

    // TODO
    public void add(Ports ports) {
        list.add(ports);
    }

    public void save(FileNode wrapper) throws IOException {
        List<String> lines;

        lines = new ArrayList<>();
        for (Ports ports : list) {
            lines.add(Integer.toString(ports.prefix));
        }
        file(wrapper).writeLines(lines);
    }

    public void append(FileNode wrapper) throws IOException {
        FileNode file;

        file = file(wrapper);
        if (file.isFile()) {
            for (String line : file.readLines()) {
                list.add(new Ports(Integer.parseInt(line.trim())));
            }
        }
    }

    public void checkFree() throws IOException {
        for (Ports ports : list) {
            ports.checkFree();
        }
    }

    public boolean contains(Ports ports) {
        return list.contains(ports);
    }



    //--
    
    public static class Ports {
        public static Ports forName(String name, Ports first, Ports last) {
            return new Ports((Math.abs(name.hashCode()) % (last.prefix - first.prefix + 1)) + first.prefix);
        }

        // TODO: private
        public final int prefix;

        public Ports(int prefix) {
            this.prefix = prefix;
        }

        public int prefix() {
            return prefix;
        }

        public Ports next() {
            return new Ports(prefix + 1);
        }

        public int tomcatHttp() {
            return prefix * 10;
        }

        public int tomcatStop() {
            return prefix * 10 + 1;
        }

        public int tomcatHttps() {
            return prefix * 10 + 3;
        }

        public int debugPort() {
            return prefix * 10 + 5;
        }

        public int jmx() {
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
            if (obj instanceof Ports) {
                return prefix == ((Ports) obj).prefix;
            }
            return false;
        }

        public boolean within(Ports first, Ports last) {
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
        public static class PortsTypeAdapter extends TypeAdapter<Ports> {
            @Override
            public void write(JsonWriter out, Ports value) throws IOException {
                out.value(value.prefix);
            }

            @Override
            public Ports read(JsonReader in) throws IOException {
                return new Ports(in.nextInt());
            }
        }
    }
}
