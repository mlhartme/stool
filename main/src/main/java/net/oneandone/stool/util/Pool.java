/*
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

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.stage.Project;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.LineFormat;
import net.oneandone.sushi.io.LineReader;

import java.io.IOException;
import java.io.Reader;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Pool {
    private static final LineFormat FMT = new LineFormat(LineFormat.LF_SEPARATOR, LineFormat.Trim.ALL, LineFormat.excludes(true));

    public static Pool loadOpt(FileNode file, int first, int last, FileNode backstages) throws IOException {
        Pool result;
        String line;

        result = new Pool(file, first, last, backstages);
        if (file.exists()) {
            try (Reader in = file.newReader(); LineReader src = new LineReader(in, FMT)) {
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

    public FileNode getFile() {
        return file;
    }

    // TODO: ugly reference to stage ...
    public Ports allocate(Project project, Map<String, Integer> fixed) throws IOException {
        // maps vhosts to docroots
        LinkedHashSet<String> names;
        Map<String, FileNode> nameWars;
        Vhost previous;
        Vhost found;
        String stageName;
        String stageId;
        List<Vhost> result;
        Integer port; // null for not fixed
        Vhost modified;
        FileNode war;

        gc();

        names = new LinkedHashSet<>();
        names.add(Ports.JMX_DEBUG);

        names.addAll(fixed.keySet());

        nameWars = new LinkedHashMap<>(project.selectedWars());
        names.addAll(nameWars.keySet());

        stageName = project.getStage().getName();
        stageId = project.getStage().getId();
        result = new ArrayList<>();
        for (String name : names) {
            war = nameWars.get(name);
            port = fixed.get(name);
            previous = lookupId(name, stageId);
            if (previous != null) {
                modified = previous.set(port, war);
                if (modified == null) {
                    // no changes
                    result.add(previous);
                } else {
                    if (port != null) {
                        if (used(port)) {
                            throw new ArgumentException("port already reserved: " + port);
                        }
                        checkFree(port);
                    }
                    result.add(modified);
                    vhosts.remove(previous);
                    vhosts.add(modified);
                }
            } else {
                if (port == null) {
                    found = allocate(name, stageName, stageId, war);
                } else {
                    found = allocate(port, name, stageId, war);
                    if (found.even != port) {
                        throw new ArgumentException("port already in use: " + port);
                    }
                }
                result.add(found);
            }
        }

        // gc this stage, i.e. remove all vhosts that are now unused
        for (int i = vhosts.size() - 1; i >= 0; i--) {
            if (stageId.equals(vhosts.get(i).id) && !result.contains(vhosts.get(i))) {
                vhosts.remove(i);
            }
        }
        save();
        return new Ports(result);
    }

    private void gc() {
        Set<String> ids;

        ids = new HashSet<>();
        for (Vhost vhost : vhosts) {
            ids.add(vhost.id);
        }
        for (String id : ids) {
            if (!backstages.join(id).isDirectory()) {
                gcId(id);
            }
        }
    }

    private void gcId(String id) {
        Vhost vhost;

        for (int i = vhosts.size() - 1; i >= 0; i--) {
            vhost = vhosts.get(i);
            if (id.equals(vhost.id)) {
                vhosts.remove(i);
            }
        }
    }

    public Vhost lookupId(String name, String id) {
        for (Vhost vhost : vhosts) {
            if (name.equals(vhost.name) && id.equals(vhost.id)) {
                return vhost;
            }
        }
        return null;
    }

    public void save() throws IOException {
        List<String> lines;

        lines = new ArrayList<>();
        for (Vhost vhost : vhosts) {
            lines.add(vhost.toLine());
        }
        file.writeLines(lines);
    }

    private Vhost allocate(String name, String stage, String id, FileNode war) throws IOException {
        return allocate(forName(name, stage), name, id, war);
    }

    private Vhost allocate(int start, String name, String id, FileNode war) throws IOException {
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
                result = new Vhost(current, name, id, war);
                vhosts.add(result);
                return result;
            }
            current += 2;
            if (current > last) {
                current = first;
            }
        } while (current != start);
        throw new IOException("cannot find free port in range [" + first + ", " + last + "[");
    }

    public int temp() throws IOException {
        int current;

        for (current = first; current <= last; current += 2) {
            if (!used(current)) {
                return current;
            }
        }
        throw new IOException("cannot find free port in range [" + first + ", " + last + "[");
    }

    public void add(Vhost vhost) {
        vhosts.add(vhost);
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
        int result;

        result = (name + stage).hashCode();
        if (result < 0) {
            result = -result;
            if (result < 0) { // happens for Integer.MIN_VALUE
                result = 0;
            }
        }
        return ((result % (last - first + 1)) + first) & 0xfffffffe;
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

    public Ports stageOpt(String id) {
        List<Vhost> result;

        result = new ArrayList<>();
        for (Vhost vhost : vhosts) {
            if (id.equals(vhost.id)) {
                result.add(vhost);
            }
        }
        return result.isEmpty() ? null : new Ports(result);
    }

}
