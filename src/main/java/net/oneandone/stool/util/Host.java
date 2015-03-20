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

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.util.List;

public class Host {
    public static Host forLine(World world, String line) throws IOException {
        List<String> parts;

        parts = Separator.SPACE.split(line);
        if (parts.size() != 4) {
            throw new IOException("invalid host line: " + line);
        }
        return new Host(Integer.parseInt(parts.get(0)), parts.get(1), parts.get(2), world.file(parts.get(3)));
    }

    public final int even;
    public final String vhost;
    private final String hostname;
    public final FileNode docroot;

    public Host(int even, String vhost, String hostname, FileNode docroot) {
        this.even = even;
        this.vhost = vhost;
        this.hostname = hostname;
        this.docroot = docroot;
    }

    public String appBase() {
        if (docroot.getName().equals("ROOT")) {
            return docroot.getParent().getAbsolute();
        } else {
            // to force tomcat 6 not to load catalina base and its subdirectory
            return "noSuchDirectory";
        }
    }

    public String docBase() {
        if (docroot.getName().equals("ROOT")) {
            return "ROOT";
        } else {
            return docroot.getAbsolute();
        }
    }

    public int httpPort() {
        return even;
    }

    public int httpsPort() {
        return even + 1;
    }

    public String httpUrl(boolean vhosts) {
        return "http://" + fqdn(vhosts) + ":" + httpPort();
    }

    public String httpsUrl(boolean vhosts) {
        return "https://" + fqdn(vhosts) + ":" + httpsPort();
    }

    public String fqdn(boolean vhosts) {
        if (vhosts) {
            return vhost + "." + hostname;
        } else {
            return hostname;
        }
    }

    public String toLine() {
        // CAUTION: just
        //    even + ' '
        // results in am integer!
        return Integer.toString(even) + ' ' + vhost + ' ' + hostname + ' ' + docroot;
    }

    public String toString() {
        return toLine();
    }
}
