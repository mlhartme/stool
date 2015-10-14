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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Manage ports used for one stage */
public class Ports {
    // TODO: ugly reference to stage ...
    public static Ports allocate(Stage stage) throws IOException {
        Ports previous;
        Pool pool;
        String name;
        Ports result;
        String vhost;
        int even;
        Vhost host;
        Map<String, FileNode> hosts;
        Integer i;
        Map<String, Integer> reserved;

        previous = loadOpt(stage);
        pool = stage.session.createPool();
        result = new Ports();
        hosts = new LinkedHashMap<>();
        name = stage.getName();
        hosts.put("stop+wrapper." + name, null);
        hosts.put("jmx+debug." + name, null);
        hosts.putAll(stage.selectedHosts());
        hosts.putAll(stage.extensions().vhosts(stage));
        for (Map.Entry<String, FileNode> entry : hosts.entrySet()) {
            vhost = entry.getKey();
            even = 0;
            if (previous != null) {
                host = previous.lookup(vhost);
                if (host != null) {
                    even = host.even;
                }
            }
            if (even == 0) {
                reserved = stage.session.configuration.reservedPorts;
                i = reserved.get(vhost);
                if (i != null) {
                    even = i;
                } else {
                    even = pool.allocate(vhost);
                }
            }
            result.hosts.add(Vhost.create(even, vhost, entry.getValue()));
        }
        result.save(stage.backstage);
        return result;
    }

    private static final LineFormat FMT = new LineFormat(LineFormat.LF_SEPARATOR, LineFormat.Trim.ALL, LineFormat.excludes(true));

    public static Ports loadOpt(Stage stage) throws IOException {
        FileNode file;
        Ports result;
        String line;

        file = file(stage.backstage);
        if (file.isFile()) {
            result = new Ports();
            try (Reader in = file.createReader(); LineReader src = new LineReader(in, FMT)) {
                while (true) {
                    line = src.next();
                    if (line == null) {
                        break;
                    }
                    result.hosts.add(Vhost.forLine(stage.session.console.world, line));
                }
                return result;
            }
        } else {
            return null;
        }
    }

    private static FileNode file(FileNode backstage) {
        return backstage.join("ports");
    }

    //--

    private final List<Vhost> hosts;

    public Ports() {
        this.hosts = new ArrayList<>();
    }

    public int stop() {
        return hosts.get(0).even;
    }

    public int wrapper() {
        return hosts.get(0).even + 1;
    }

    public int jmx() {
        return hosts.get(1).even;
    }

    public int debug() {
        return hosts.get(1).even + 1;
    }

    public List<Vhost> hosts() {
        return hosts;
    }

    public Vhost mainHost() {
        for (Vhost host : hosts()) {
            if (host.isWebapp()) {
                return host;
            }
        }
        throw new IllegalStateException();
    }

    public Vhost lookup(String vhost) {
        for (Vhost host : hosts) {
            if (vhost.equals(host.vhost())) {
                return host;
            }
        }
        return null;
    }

    //--

    private void save(FileNode backstage) throws IOException {
        List<String> lines;

        lines = new ArrayList<>();
        for (Vhost host : hosts) {
            lines.add(host.toLine());
        }
        Files.stoolFile(file(backstage).writeLines(lines));
    }

    //--

    public Map<String, String> urlMap(boolean https, boolean vhosts, String hostname, String suffix) {
        String name;
        Map<String, String> result;
        int idx;

        result = new LinkedHashMap<>();
        for (Vhost host : hosts()) {
            if (host.isWebapp()) {
                name = host.vhost();
                idx = host.vhost().indexOf('.');
                if (idx != -1) {
                    name = name.substring(0, idx);
                }
                result.put(name, host.httpUrl(vhosts, hostname) + suffix);
                if (https) {
                    result.put(name + " SSL", host.httpsUrl(vhosts, hostname) + suffix);
                }
            }
        }
        return result;
    }

    //--

    public static void addUsed(FileNode backstage, List<Integer> lazyUsed) throws IOException {
        FileNode file;
        String line;
        int idx;

        file = file(backstage);
        if (file.isFile()) {
            try (Reader in = file.createReader(); LineReader src = new LineReader(in, FMT)) {
                while (true) {
                    line = src.next();
                    if (line == null) {
                        break;
                    }
                    idx = line.indexOf(' ');
                    if (idx != -1) {
                        line = line.substring(0, idx);
                    }
                    lazyUsed.add(Integer.parseInt(line));
                }
            }
        }
    }
}
