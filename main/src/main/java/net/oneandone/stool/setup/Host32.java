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

package net.oneandone.stool.setup;

import net.oneandone.stool.util.Vhost;
import net.oneandone.sushi.fs.LineFormat;
import net.oneandone.sushi.fs.LineReader;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Ports file line in Stool 3.2 */
public class Host32 {
    private static final LineFormat FMT = new LineFormat(LineFormat.LF_SEPARATOR, LineFormat.Trim.ALL, LineFormat.excludes(true));

    public static List<Host32> load(Node file) throws IOException {
        List<Host32> result;
        String line;

        result = new ArrayList<>();
        try (Reader in = file.createReader(); LineReader src = new LineReader(in, FMT)) {
            while (true) {
                line = src.next();
                if (line == null) {
                    break;
                }
                result.add(Host32.forLine(line));
            }
            return result;
        }
    }

    private static Host32 forLine(String line) throws IOException {
        List<String> parts;
        String docroot;

        parts = Separator.SPACE.split(line);
        switch (parts.size()) {
            case 3:
                docroot = null;
                break;
            case 4:
                docroot = parts.get(3);
                break;
            default:
                throw new IOException("invalid host line: " + line);
        }
        return new Host32(Integer.parseInt(parts.get(0)), parts.get(1), parts.get(2), docroot);
    }

    private final int even;

    private final String vhost;

    private final String hostname;

    /**
     * null for ports that have no domain
     */
    private final String docroot;

    public Host32(int even, String vhost, String hostname, String docroot) {
        this.even = even;
        this.vhost = vhost;
        this.hostname = hostname;
        this.docroot = docroot;
    }

    public Vhost upgrade(Node backstage) throws IOException {
        String name;
        String stageName;

        stageName = backstage.getName();
        if (!vhost.endsWith("." + stageName)) {
            throw new IllegalStateException(vhost + " does not end with " + stageName);
        }
        name = vhost.substring(0, vhost.length() - stageName.length() - 1);
        return new Vhost(even, name, stageName, null);
    }
}