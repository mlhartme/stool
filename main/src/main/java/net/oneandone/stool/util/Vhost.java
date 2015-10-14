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

public class Vhost {
    public static Vhost forLine(World world, String line) throws IOException {
        List<String> parts;
        FileNode docroot;

        parts = Separator.SPACE.split(line);
        switch (parts.size()) {
            case 2:
                docroot = null;
                break;
            case 3:
                docroot = world.file(parts.get(2));
                break;
            default:
                throw new IOException("invalid vhost line: " + line);
        }
        return Vhost.create(Integer.parseInt(parts.get(0)), parts.get(1), docroot);
    }

    public static Vhost create(int even, String vhost, FileNode docroot) {
        int idx;

        idx = vhost.lastIndexOf('.');
        if (idx == -1) {
            throw new IllegalStateException(vhost);
        }
        return new Vhost(even, vhost.substring(0, idx), vhost.substring(idx + 1), docroot);
    }

    public final int even;

    public final String name;

    public final String stage;

    /** null for ports that have no domain */
    public final FileNode docroot;

    public Vhost(int even, String name, String stage, FileNode docroot) {
        this.even = even;
        this.name = name;
        this.stage = stage;
        this.docroot = docroot;
    }

    public boolean isWebapp() {
        return docroot != null;
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

    public String httpUrl(boolean vhosts, String hostname) {
        return "http://" + fqdn(vhosts, hostname) + ":" + httpPort();
    }

    public String httpsUrl(boolean vhosts, String hostname) {
        return "https://" + fqdn(vhosts, hostname) + ":" + httpsPort();
    }

    public String fqdn(boolean vhosts, String hostname) {
        if (vhosts) {
            return name + "." + stage + "." + hostname;
        } else {
            return hostname;
        }
    }

    public String toLine() {
        // CAUTION: just
        //    even + ' '
        // results in am integer!
        return Integer.toString(even) + ' ' + vhost() + (docroot == null ? "" : " " + docroot);
    }

    public String toString() {
        return toLine();
    }

    public String vhost() {
        return name + "." + stage;
    }
}
