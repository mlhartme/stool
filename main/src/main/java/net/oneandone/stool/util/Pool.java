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

import com.google.gson.JsonObject;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.docker.Engine;
import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.LineFormat;
import net.oneandone.sushi.io.LineReader;

import java.io.IOException;
import java.io.Reader;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Pool {
    public static Pool load(Engine engine, int first, int last) throws IOException {
        JsonObject labels;
        Ports ports;
        Pool result;
        String stage;
        String app;

        result = new Pool(first, last);
        for (String container : engine.containerList(Stage.LABEL_STOOL).keySet()) {
            labels = engine.containerInspect(container, false).get("Config").getAsJsonObject().get("Labels").getAsJsonObject();
            stage = labels.get(Stage.LABEL_STAGE).getAsString();
            app = labels.get(Stage.LABEL_APP).getAsString();
            ports = Ports.fromHostLabels(labels);
            if (ports.https != ports.http + 1) {
                throw new IllegalStateException(ports.toString());
            }
            if (ports.debug != ports.jmxmp + 1) {
                throw new IllegalStateException(ports.toString());
            }
            result.vhosts.add(new Vhost(ports.http, app, stage, true));
            result.vhosts.add(new Vhost(ports.jmxmp, app, stage, false));
        }
        return result;
    }

    //--

    private final int first;
    private final int last;
    private final List<Vhost> vhosts;

    public Pool(int first, int last) {
        if (first % 2 != 0) {
            throw new IllegalArgumentException("even port expected: " + first);
        }
        if (last % 2 != 1) {
            throw new IllegalArgumentException("odd port expected: " + last);
        }
        this.first = first;
        this.last = last;
        this.vhosts = new ArrayList<>();
    }

    // TODO: ugly reference to stage ...
    public Ports allocate(Stage stage, String app, Integer port) throws IOException {
        String stageName;
        String stageId;
        Vhost webapp;
        Vhost jmxDebug;

        stageName = stage.getName();
        stageId = stage.getId();
        webapp = allocateVhost(app, stageName, stageId, true, port);
        jmxDebug = allocateVhost(app, stageName, stageId, false, null);
        return Ports.forVhosts(webapp, jmxDebug);
    }

    private Vhost allocateVhost(String app, String stageName, String stageId, boolean webapp, Integer port) throws IOException {
        Vhost previous;
        Vhost modified;
        Vhost found;

        previous = lookupId(app, stageId, webapp);
        if (previous != null) {
            modified = previous.set(null, webapp);
            if (modified == null) {
                return previous;
            } else {
                if (port != null) {
                    if (used(port)) {
                        throw new ArgumentException("port already reserved: " + port);
                    }
                    checkFree(port);
                }
                vhosts.remove(previous);
                vhosts.add(modified);
                return modified;
            }
        } else {
            if (port == null) {
                found = allocate(app, stageName, stageId, webapp);
            } else {
                found = allocate(port, app, stageId, webapp);
                if (found.even != port) {
                    throw new ArgumentException("port already in use: " + port);
                }
            }
            return found;
        }
    }

    private Vhost allocate(String app, String stage, String id, boolean webapp) throws IOException {
        return allocate(startPortForApp(app, stage), app, id, webapp);
    }

    private Vhost allocate(int start, String app, String id, boolean webapp) throws IOException {
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
                result = new Vhost(current, app, id, webapp);
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

    private Vhost lookupId(String app, String id, boolean webapp) {
        for (Vhost vhost : vhosts) {
            if (app.equals(vhost.app) && id.equals(vhost.id) && webapp == vhost.webapp) {
                return vhost;
            }
        }
        return null;
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

    private int startPortForApp(String app, String stage) {
        int result;

        result = (app + stage).hashCode();
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

    public Map<String, Ports> stage(String id) {
        Map<String, List<Vhost>> map;
        List<Vhost> list;
        Map<String, Ports> result;

        map = new HashMap<>();
        for (Vhost vhost : vhosts) {
            if (id.equals(vhost.id)) {
                list = map.get(vhost.app);
                if (list == null) {
                    list = new ArrayList<>();
                    map.put(vhost.app, list);
                }
                list.add(vhost);
            }
        }
        result = new HashMap<>(map.size());
        for (Map.Entry<String, List<Vhost>> entry : map.entrySet()) {
            result.put(entry.getKey(), Ports.forVhosts(entry.getValue()));
        }
        return result;
    }
}
