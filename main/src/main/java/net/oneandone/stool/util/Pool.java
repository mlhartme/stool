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
import net.oneandone.sushi.fs.LineFormat;
import net.oneandone.sushi.fs.LineReader;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Reader;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Pool {
    private static final LineFormat FMT = new LineFormat(LineFormat.LF_SEPARATOR, LineFormat.Trim.ALL, LineFormat.excludes(true));

    public static Pool loadOpt(FileNode file, int first, int last, FileNode backstages) throws IOException {
        Pool result;
        String line;

        result = new Pool(file, first, last, backstages);
        if (file.exists()) {
            try (Reader in = file.createReader(); LineReader src = new LineReader(in, FMT)) {
                while (true) {
                    line = src.next();
                    if (line == null) {
                        break;
                    }
                    result.vhosts.add(Vhost.forLine(file.getWorld(), line));
                }
            }
        }
        return result;
    }

    //--

    private final FileNode file;
    private final int first;
    private final int last;
    private final List<Vhost> vhosts;
    private final FileNode backstages;

    public Pool(FileNode file, int first, int last, FileNode backstages) {
        if (first % 2 != 0) {
            throw new IllegalArgumentException("even port expected: " + first);
        }
        if (last % 2 != 1) {
            throw new IllegalArgumentException("odd port expected: " + last);
        }
        this.file = file;
        this.first = first;
        this.last = last;
        this.backstages = backstages;
        this.vhosts = new ArrayList<>();
    }

    // TODO: ugly reference to stage ...
    public Ports allocate(Stage stage) throws IOException {
        // maps vhosts to docroots
        Map<String, FileNode> map;
        Vhost previous;
        String stageName;
        List<Vhost> result;
        String vhost;

        gc();
        map = new LinkedHashMap<>();
        result = new ArrayList<>();
        stageName = stage.getName();
        map.put(Ports.STOP_WRAPPER, null);
        map.put(Ports.JMX_DEBUG, null);
        map.putAll(stage.selectedVhosts());
        map.putAll(stage.extensions().vhosts(stage));
        for (Map.Entry<String, FileNode> entry : map.entrySet()) {
            vhost = entry.getKey();
            previous = lookup(vhost, stageName);
            if (previous != null) {
                if (Objects.equals(entry.getValue(), previous.docroot)) {
                    result.add(previous);
                } else {
                    vhosts.remove(previous);
                }
            } else {
                result.add(allocate(vhost, stageName, entry.getValue()));
            }
        }
        save();
        return new Ports(result);
    }

    private void gc() {
        Set<String> used;

        used = new HashSet<>();
        for (Vhost vhost : vhosts) {
            used.add(vhost.stage);
        }
        for (String stage : used) {
            if (!backstages.join(stage).isDirectory()) {
                gc(stage);
            }
        }
    }

    private void gc(String stage) {
        Vhost vhost;

        for (int i = vhosts.size() - 1; i >= 0; i--) {
            vhost = vhosts.get(i);
            if (stage.equals(vhost.stage)) {
                vhosts.remove(i);
            }
        }
    }

    public Vhost lookup(String name, String stage) {
        for (Vhost vhost : vhosts) {
            if (name.equals(vhost.name) && stage.equals(vhost.name)) {
                return vhost;
            }
        }
        return null;
    }

    private void save() throws IOException {
        List<String> lines;

        lines = new ArrayList<>();
        for (Vhost vhost : vhosts) {
            lines.add(vhost.toLine());
        }
        Files.stoolFile(file.writeLines(lines));
    }

    private Vhost allocate(String name, String stage, FileNode docroot) throws IOException {
        return allocate(forName(name, stage), name, stage, docroot);
    }

    private Vhost allocate(int start, String name, String stage, FileNode docroot) throws IOException {
        int current;
        Vhost result;

        if ((start < first) || (start > last)) {
            throw new IllegalArgumentException("ports out of range: " + start);
        }
        if (start % 2 != 0) {
            throw new IllegalArgumentException("even port expected: " + start);
        }
        current = start;
        do {
            if (!used(current)) {
                checkFree(current);
                checkFree(current + 1);
                result = new Vhost(current, name, stage, docroot);
                vhosts.add(result);
                return result;
            }
            current += 2;
            if (current > last) {
                current = first;
            }
        } while (current != start);
        throw new IOException("cannot allocate port");
    }

    private boolean used(int even) {
        for (Vhost vhost : vhosts) {
            if (vhost.even == even) {
                return true;
            }
        }
        return false;
    }

    private int forName(String name, String stage) {
        return ((Math.abs((name + stage).hashCode()) % (last - first + 1)) + first) & 0xfffffffe;
    }

    //--

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

    public Ports stageOpt(String stage) {
        List<Vhost> result;

        result = new ArrayList<>();
        for (Vhost vhost : vhosts) {
            if (stage.equals(vhost.stage)) {
                result.add(vhost);
            }
        }
        return result.isEmpty() ? null : new Ports(result);
    }
}
