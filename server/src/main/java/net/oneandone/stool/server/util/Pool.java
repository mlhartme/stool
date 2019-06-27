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
package net.oneandone.stool.server.util;

import net.oneandone.stool.server.docker.ContainerInfo;
import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.stage.Stage;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Pool {
    public static Pool load(Engine engine, int first, int last) throws IOException {
        Map<String, String> labels;
        Pool result;
        String stage;
        String app;

        result = new Pool(first, last);
        for (ContainerInfo info : engine.containerList(Stage.CONTAINER_LABEL_IMAGE).values()) {
            labels = info.labels;
            stage = labels.get(Stage.CONTAINER_LABEL_STAGE);
            app = labels.get(Stage.CONTAINER_LABEL_APP);
            result.datas.add(new Data(stage, app, Ports.fromUsedLabels(labels)));
        }
        return result;
    }

    public static class Data {
        public final String stage;
        public final String app;
        public final Ports ports;

        public Data(String stage, String app, Ports ports) {
            this.stage = stage;
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
        String name;
        Ports previous;

        name = stage.getName();
        previous = lookup(name, app);
        if (previous != null) {
            if ((http != -1 && http != previous.http) || (https != -1 && https != previous.http)) {
                previous = null;
                remove(name, app);
            }
        }
        if (previous != null) {
            return previous;
        } else {
            return allocate(startPortForApp(app, stage.getName()), app, name, http, https);
        }
    }

    private Ports lookup(String name, String app) {
        for (Data data : datas) {
            if (name.equals(data.stage) && app.equals(data.app)) {
                return data.ports;
            }
        }
        return null;
    }
    private boolean remove(String name, String app) {
        Data data;

        for (int i = 0; i < datas.size(); i++) {
            data = datas.get(i);
            if (name.equals(data.stage) && app.equals(data.app)) {
                datas.remove(i);
                return true;
            }
        }
        return false;
    }

    /** @return ports with all ports allocated */
    private Ports allocate(int start, String app, String stage, int fixedHttp, int fixedHttps) throws IOException {
        Ports ports;
        List<Integer> ignores;
        int http;
        int https;
        int jmxmp;
        int debug;

        if ((start < first) || (start > last)) {
            throw new IllegalArgumentException("ports out of range: " + start);
        }
        ignores = new ArrayList<>();

        http = one(fixedHttp != -1 ? fixedHttp : start, ignores);
        if (fixedHttp != -1 && http != fixedHttp) {
            throw new IOException("http port is already allocated: " + fixedHttp);
        }
        ignores.add(http);

        https = one(fixedHttps != -1 ? fixedHttps : start, ignores);
        if (fixedHttps != -1 && https != fixedHttps) {
            throw new IOException("https port is already allocated: " + fixedHttps);
        }
        ignores.add(https);

        jmxmp = one(start, ignores);
        ignores.add(jmxmp);

        debug = one(start, ignores);

        ports = new Ports(http, https, jmxmp, debug);
        datas.add(new Data(stage, app, ports));
        return ports;
    }

    private int one(int start, List<Integer> ignores) throws IOException {
        int current;

        if ((start < first) || (start > last)) {
            throw new IllegalArgumentException(start + ": port out of range " + first + " ... " + last);
        }
        current = start;
        do {
            if (!ignores.contains(current) && !isAllocated(current)) {
                checkFree(current);
                return current;
            }
            current = current < last ? current + 1 : first;
        } while (current != start);
        throw new IOException("no free port in range " + first + " .. " + last);
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

    public Map<String, Ports> stage(String name) {
        Map<String, Ports> result;

        result = new HashMap<>();
        for (Data data : datas) {
            if (name.equals(data.stage)) {
                result.put(data.app, data.ports);
            }
        }
        return result;
    }
}
