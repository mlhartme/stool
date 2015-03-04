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
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Ports {
    // TODO: ugly reference to stage ...
    public static Ports allocate(Stage stage) throws IOException {
        Ports previous;
        Pool pool;
        String name;
        Ports result;
        String vhost;
        int even;
        Host host;
        Map<String, String> hosts;

        previous = loadOpt(stage);
        pool = stage.session.createPool();
        if (previous != null) {
            result = new Ports(previous.stopWrapper, previous.jmxDebug);
        } else {
            name = stage.getName();
            result = new Ports(pool.allocate(".stop.wrapper." + name), pool.allocate(".jmx.debug." + name));
        }
        hosts = stage.selectedHosts();
        if (stage.config().pustefixEditor) {
            hosts.put("cms." + stage.getName(), stage.editorDocroot().getAbsolute());
        }
        for (Map.Entry<String, String> entry : hosts.entrySet()) {
            vhost = entry.getKey();
            if (stage.isOverview()) {
                if (hosts.size() != 1) {
                    throw new IllegalStateException(hosts.toString());
                }
                even = stage.session.configuration.portOverview;
            } else {
                even = 0;
                if (previous != null) {
                    host = previous.lookup(vhost);
                    if (host != null) {
                        even = host.even;
                    }
                }
                if (even == 0) {
                    even = pool.allocate(vhost);
                }
            }
            result.hosts.add(new Host(even, vhost, stage.session.configuration.hostname, entry.getValue()));
        }
        result.save(stage.wrapper);
        return result;
    }

    private static final LineFormat FMT = new LineFormat(LineFormat.LF_SEPARATOR, LineFormat.Trim.ALL, LineFormat.excludes(true));

    public static Ports loadOpt(Stage stage) throws IOException {
        FileNode file;
        Ports result;
        String line;

        file = file(stage.wrapper);
        if (file.isFile()) {
            try (Reader in = file.createReader(); LineReader src = new LineReader(in, FMT)) {
                result = new Ports(Integer.parseInt(src.next()), Integer.parseInt(src.next()));
                while (true) {
                    line = src.next();
                    if (line == null) {
                        break;
                    }
                    result.hosts.add(Host.forLine(line));
                }
                return result;
            }
        } else {
            return null;
        }
    }

    private static FileNode file(FileNode wrapper) {
        return wrapper.join("ports");
    }

    //--

    private final int stopWrapper;
    private final int jmxDebug;
    private final List<Host> hosts;

    public Ports(int stopWrapper, int jmxDebug) {
        this.stopWrapper = stopWrapper;
        this.jmxDebug = jmxDebug;
        this.hosts = new ArrayList<>();
    }

    public int stop() {
        return stopWrapper;
    }

    public int wrapper() {
        return stopWrapper + 1;
    }

    public int jmx() {
        return jmxDebug;
    }

    public int debug() {
        return jmxDebug + 1;
    }

    public List<Host> hosts() {
        return hosts;
    }

    public Host mainHost() {
        return hosts().get(0);
    }

    public Host lookup(String vhost) {
        for (Host host : hosts) {
            if (vhost.equals(host.vhost)) {
                return host;
            }
        }
        return null;
    }

    //--

    private void save(FileNode wrapper) throws IOException {
        List<String> lines;

        lines = new ArrayList<>();
        lines.add(Integer.toString(stopWrapper));
        lines.add(Integer.toString(jmxDebug));
        for (Host host : hosts) {
            lines.add(host.toLine());
        }
        file(wrapper).writeLines(lines);
    }

    //--

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

    //--

    public static void addUsed(FileNode wrapper, List<Integer> lazyUsed) throws IOException {
        FileNode file;
        String line;
        int idx;

        file = file(wrapper);
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
