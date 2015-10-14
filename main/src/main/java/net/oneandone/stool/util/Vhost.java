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

import java.io.IOException;

/** immutable */
public class Vhost {
    private static final char SEP = ' ';

    // parses   <even> <name> <stage> [<docroot>]
    public static Vhost forLine(World world, String line) throws IOException {
        int afterEven;
        int afterName;
        int afterStage;
        int even;
        String name;
        String stage;
        FileNode docroot;

        afterEven = line.indexOf(SEP);
        if (afterEven == -1) {
            throw new IllegalArgumentException("invalid vhost line: " + line);
        }
        even = Integer.parseInt(line.substring(0, afterEven));

        afterName = line.indexOf(SEP, afterEven + 1);
        if (afterName == -1) {
            throw new IllegalArgumentException("invalid vhost line: " + line);
        }
        name = line.substring(afterEven + 1, afterName);

        afterStage = line.indexOf(SEP, afterName + 1);
        if (afterStage == -1) {
            stage = line.substring(afterName + 1);
            docroot = null;
        } else {
            stage = line.substring(afterName + 1, afterStage);
            docroot = world.file(line.substring(afterStage + 1));
        }
        return new Vhost(even, name, stage, docroot);
    }

    public final int even;

    public final String name;

    public final String stage;

    /** null for ports that have no domain */
    public final FileNode docroot;

    public Vhost(int even, String name, String stage, FileNode docroot) {
        if (name.indexOf(SEP) != -1) {
            throw new IllegalArgumentException(name);
        }
        if (stage.indexOf(SEP) != -1) {
            throw new IllegalArgumentException(stage);
        }
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
        //    even + SEP
        // is an integer addition!
        return Integer.toString(even) + SEP + name + SEP + stage + (docroot == null ? "" : Character.toString(SEP) + docroot);
    }

    public String toString() {
        return toLine();
    }
}
