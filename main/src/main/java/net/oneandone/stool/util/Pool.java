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

    /** first port, inclusive */
    private final int first;
    /** last port, inclusive */
    private final int last;
    private final List<Data> datas;

    public Pool(int first, int last) {
        this.first = first;
        this.last = last;
        this.datas = new ArrayList<>();
    }

    public Ports allocate(Stage stage, String app, int http, int https) throws IOException {
        String id;
        Ports previous;

        id = stage.getId();
        previous = lookup(id, app);
        if (previous != null) {
            return previous;
        } else {
            return allocate(startPortForApp(app, stage.getName()), app, id);
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

    /** @return ports with all ports allocated */
    private Ports allocate(int start, String app, String id) throws IOException {
        Ports ports;
        List<Integer> list;

        if ((start < first) || (start > last)) {
            throw new IllegalArgumentException("ports out of range: " + start);
        }
        list = allocateList(start, 4);
        if (list.size() < 4) {
            throw new IOException("cannot find 4 free port in range [" + first + ", " + last + "[");
        }
        ports = new Ports(list.get(0), list.get(1), list.get(2), list.get(3));
        datas.add(new Data(id, app, ports));
        return ports;
    }

    private List<Integer> allocateList(int start, int count) throws IOException {
        int current;
        List<Integer> result;

        if ((start < first) || (start > last)) {
            throw new IllegalArgumentException("ports out of range: " + start);
        }
        result = new ArrayList<>();
        current = start;
        do {
            if (!isAllocated(current)) {
                checkFree(current);
                result.add(current);
                if (result.size() >= count) {
                    break;
                }
            }
            current = current < last ? current + 1 : first;
        } while (current != start);
        return result;
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
