/**
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.stool.util;

import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Ports {
    private static final String STOP_WRAPPER = ".stop-wrapper";
    private static final String JMX_DEBUG = ".jmx-debug";

    private static boolean isInternal(String name) {
        return name.startsWith(".");
    }

    // TODO: ugly reference to stage ...
    public static Ports forStage(Stage stage) throws IOException {
        Session session;
        Ports existing;
        Ports result;
        Set<Integer> used;
        List<String> names;
        Integer even;

        session = stage.session;
        result = new Ports(stage.session.configuration.hostname);
        existing = Ports.load(result.hostname, stage.wrapper);
        used = null;
        names = new ArrayList<>();
        names.add(STOP_WRAPPER);
        names.add(JMX_DEBUG);
        names.addAll(stage.selectedHosts().keySet());
        if (stage.config().pustefixEditor) {
            names.add("cms." + stage.getName());
        }
        for (String name : names) {
            even = existing.evens.get(name);
            if (even == null) {
                if (used == null) {
                    used = used(session.getWrappers());
                    used.add(session.configuration.portOverview);
                }
                if (!isInternal(name) && stage.isOverview()) {
                    even = session.configuration.portOverview;
                } else {
                    even = allocate(used, name, session.configuration.portFirst, session.configuration.portLast);
                }
            }
            result.evens.put(name, even);
        }
        result.save(stage.wrapper);
        return result;
    }

    public static Ports load(String hostname, FileNode wrapper) throws IOException {
        Ports result;

        result = new Ports(hostname);
        result.append(wrapper);
        return result;
    }

    private static FileNode file(FileNode wrapper) {
        return wrapper.join("ports");
    }

    //--

    private final String hostname;

    /**
     * Maps names to stores even ports nummers; its successor is also considered as allocated.
     * Names starting with . are internal names, all other names are vhosts without the hostname */
    private final LinkedHashMap<String, Integer> evens;

    public Ports(String hostname) {
        this.hostname = hostname;
        this.evens = new LinkedHashMap<>();
    }

    public int stop() {
        return evens.get(STOP_WRAPPER);
    }

    public int wrapper() {
        return evens.get(STOP_WRAPPER) + 1;
    }

    public int jmx() {
        return evens.get(JMX_DEBUG);
    }

    public int debug() {
        return evens.get(JMX_DEBUG) + 1;
    }

    public List<Host> hosts() {
        List<Host> result;
        int even;

        result = new ArrayList<>();
        for (String name : evens.keySet()) {
            if (!isInternal(name)) {
                even = evens.get(name);
                result.add(new Host(name, hostname, "TODO", even, even + 1));
            }
        }
        return result;
    }

    public Host mainHost() {
        return hosts().get(0);
    }

    //--

    private void save(FileNode wrapper) throws IOException {
        List<String> lines;

        lines = new ArrayList<>();
        for (Map.Entry<String, Integer> even : evens.entrySet()) {
            lines.add(even.getValue() + " " + even.getKey());
        }
        file(wrapper).writeLines(lines);
    }

    private void append(FileNode wrapper) throws IOException {
        FileNode file;
        int idx;

        file = file(wrapper);
        if (file.isFile()) {
            for (String line : file.readLines()) {
                idx = line.indexOf(' ');
                evens.put(line.substring(idx + 1).trim(), Integer.parseInt(line.substring(0, idx).trim()));
            }
        }
    }

    //--

    public static Set<Integer> used(List<FileNode> wrappers) throws IOException {
        Set<Integer> result;

        result = new HashSet<>();
        for (FileNode wrapper : wrappers) {
            result.addAll(load("notused", wrapper).evens.values());
        }
        return result;
    }

    public static int allocate(Collection<Integer> used, String name, int first, int last) throws IOException {
        return allocate(used, forName(name, first, last), first, last);
    }

    public static int allocate(Collection<Integer> used, int start, int first, int last) throws IOException {
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
            if (!used.contains(current)) {
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

    public static int forName(String name, int first, int last) {
        return ((Math.abs(name.hashCode()) % (last - first + 1)) + first) & 0xfffffffe;
    }

    /**
     * See http://stackoverflow.com/questions/434718/sockets-discover-port-availability-using-java
     */
    public static void checkFree(int port) throws IOException {
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

    public List<String> allUrls(boolean https, boolean vhosts, String suffix) {
        List<String> result;

        result = new ArrayList<>();
        for (Host host : hosts()) {
            result.add(host.httpUrl(vhosts) + suffix);
            if (https) {
                result.add(host.httpsUrl(vhosts) + suffix);
            }
        }
        return result;
    }
}
