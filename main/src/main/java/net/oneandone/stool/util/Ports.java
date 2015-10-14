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
        String stageName;
        Ports result;
        String vhoststr;
        int even;
        Vhost vhost;
        Map<String, FileNode> vhosts;
        Integer i;
        Map<String, Integer> reserved;

        previous = loadOpt(stage);
        pool = stage.session.createPool();
        result = new Ports();
        vhosts = new LinkedHashMap<>();
        stageName = stage.getName();
        vhosts.put("stop+wrapper", null);
        vhosts.put("jmx+debug", null);
        vhosts.putAll(stage.selectedVhosts());
        vhosts.putAll(stage.extensions().vhosts(stage));
        for (Map.Entry<String, FileNode> entry : vhosts.entrySet()) {
            vhoststr = entry.getKey();
            even = 0;
            if (previous != null) {
                vhost = previous.lookup(vhoststr, stageName);
                if (vhost != null) {
                    even = vhost.even;
                }
            }
            if (even == 0) {
                reserved = stage.session.configuration.reservedPorts;
                i = reserved.get(vhoststr + "." + stageName);
                if (i != null) {
                    even = i;
                } else {
                    even = pool.allocate(vhoststr);
                }
            }
            result.vhosts.add(new Vhost(even, vhoststr, stageName, entry.getValue()));
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
                    result.vhosts.add(Vhost.forLine(stage.session.console.world, line));
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

    private final List<Vhost> vhosts;

    public Ports() {
        this.vhosts = new ArrayList<>();
    }

    public int stop() {
        return vhosts.get(0).even;
    }

    public int wrapper() {
        return vhosts.get(0).even + 1;
    }

    public int jmx() {
        return vhosts.get(1).even;
    }

    public int debug() {
        return vhosts.get(1).even + 1;
    }

    public List<Vhost> vhosts() {
        return vhosts;
    }

    public Vhost firstWebapp() {
        for (Vhost vhost : vhosts()) {
            if (vhost.isWebapp()) {
                return vhost;
            }
        }
        throw new IllegalStateException();
    }

    public Vhost lookup(String name, String stage) {
        for (Vhost vhost : vhosts) {
            if (name.equals(vhost.name) && stage.equals(vhost.name)) {
                return vhost;
            }
        }
        return null;
    }

    //--

    private void save(FileNode backstage) throws IOException {
        List<String> lines;

        lines = new ArrayList<>();
        for (Vhost vhost : vhosts) {
            lines.add(vhost.toLine());
        }
        Files.stoolFile(file(backstage).writeLines(lines));
    }

    //--

    public Map<String, String> urlMap(boolean https, boolean vhosts, String hostname, String suffix) {
        Map<String, String> result;

        result = new LinkedHashMap<>();
        for (Vhost vhost : vhosts()) {
            if (vhost.isWebapp()) {
                result.put(vhost.name, vhost.httpUrl(vhosts, hostname) + suffix);
                if (https) {
                    result.put(vhost.name + " SSL", vhost.httpsUrl(vhosts, hostname) + suffix);
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
