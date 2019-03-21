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
import net.oneandone.stool.docker.Engine;
import net.oneandone.stool.stage.Stage;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Pool {
    public static Pool load(Engine engine, int first, int last) throws IOException {
        JsonObject labels;
        Pool result;
        String stage;
        String app;

        result = new Pool(first, last);
        for (String container : engine.containerList(Stage.LABEL_STOOL).keySet()) {
            labels = engine.containerInspect(container, false).get("Config").getAsJsonObject().get("Labels").getAsJsonObject();
            stage = labels.get(Stage.LABEL_STAGE).getAsString();
            app = labels.get(Stage.LABEL_APP).getAsString();
            result.datas.add(new Data(stage, app, Ports.fromHostLabels(labels)));
        }
        return result;
    }

    public static class Data {
        public final String id;
        public final String app;
        public final Ports ports;

        public Data(String id, String app, Ports ports) {
            this.id = id;
            this.app = app;
            this.ports = ports;
        }
    }
    //--

    private final int first;
    private final int last;
    private final List<Data> datas;

    public Pool(int first, int last) {
        if (first % 2 != 0) {
            throw new IllegalArgumentException("even port expected: " + first);
        }
        if (last % 2 != 1) {
            throw new IllegalArgumentException("odd port expected: " + last);
        }
        this.first = first;
        this.last = last;
        this.datas = new ArrayList<>();
    }

    // TODO: ugly reference to stage ...
    public Ports allocate(Stage stage, String app) throws IOException {
        String stageId;
        Ports previous;

        stageId = stage.getId();
        previous = lookup(stageId, app);
        if (previous != null) {
            return previous; // TODO: add more ports
        } else {
            return allocate(startPortForApp(app, stage.getName()), app, stageId);
        }
    }

    private Ports lookup(String id, String app) {
        for (Data data : datas) {
            if (id.equals(data.id) && app.equals(data.app)) {
                return data.ports;
            }
        }
        return null;
    }

    private Ports allocate(int start, String app, String id) throws IOException {
        Ports ports;
        int http;
        int https;
        int jmxmp;
        int debug;
        List<Integer> ignores;

        if ((start < first) || (start > last)) {
            throw new IllegalArgumentException("ports out of range: " + start);
        }
        ignores = new ArrayList<>();
        http = allocateOne(start, ignores);
        ignores.add(http);
        https = allocateOne(start, ignores);
        ignores.add(https);
        jmxmp = allocateOne(start, ignores);
        ignores.add(jmxmp);
        debug = allocateOne(start, ignores);
        ports = new Ports(http, https, jmxmp, debug);
        datas.add(new Data(id, app, ports));
        return ports;
    }

    private int allocateOne(int start, List<Integer> ignores) throws IOException {
        int current;

        if ((start < first) || (start > last)) {
            throw new IllegalArgumentException("ports out of range: " + start);
        }
        current = start;
        do {
            if (!ignores.contains(current) && !isAllocated(current)) {
                checkFree(current);
                return current;
            }
            if (current < last) {
                current++;
            } else {
                current = first;
            }
        } while (current != start);
        throw new IOException("cannot find free port in range [" + first + ", " + last + "[");
    }

    public int temp() throws IOException {
        int current;

        for (current = first; current <= last; current += 2) {
            if (!isAllocated(current)) {
                return current;
            }
        }
        throw new IOException("cannot find free port in range [" + first + ", " + last + "[");
    }

    private boolean isAllocated(int port) {
        for (Data data : datas) {
            if (data.ports.contains(port)) {
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
        Map<String, Ports> result;

        result = new HashMap<>();
        for (Data data : datas) {
            if (id.equals(data.id)) {
                result.put(data.app, data.ports);
            }
        }
        return result;
    }
}
