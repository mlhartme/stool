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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** immutable */
public class Vhost {
    private static final char SEP = ' ';

    // parses   <even> <name> <stage> [<docroot>]
    public static Vhost forLine(World world, String line) {
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
        return "http://" + fqdnHttpPort(vhosts, hostname);
    }

    public String fqdnHttpPort(boolean vhosts, String hostname) {
        return fqdn(vhosts, hostname) + ":" + httpPort();
    }

    public String httpsUrl(boolean vhosts, String hostname) {
        return "https://" + fqdnHttpsPort(vhosts, hostname);
    }

    public String fqdnHttpsPort(boolean vhosts, String hostname) {
        return fqdn(vhosts, hostname) + ":" + httpsPort();
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
        return Integer.toString(even) + SEP + name + SEP + stage + (docroot == null ? "" : Character.toString(SEP) + docroot.getAbsolute());
    }

    public String toString() {
        return toLine();
    }

    /** null if not modified */
    public Vhost set(Integer newEven, FileNode newDocroot) {
        if (Objects.equals(this.docroot, newDocroot) && (newEven == null || newEven == even)) {
            return null;
        }
        return new Vhost(newEven == null ? even : newEven, name, stage, newDocroot);
    }

    public Map<String, String> urlMap(String hostname, String url) {
        Map<String, String> result;
        Map<Character, String> map;
        List<String> all;
        List<String> http;
        List<String> https;

        result = new LinkedHashMap<>();
        map = new HashMap<>();
        map.put('h', hostname);
        map.put('a', name);
        map.put('s', stage);
        map.put('p', "%p");
        all = Url.parse(url).sustitute(map).map();
        http = new ArrayList<>();
        https = new ArrayList<>();
        for (String u : all) {
            if (u.startsWith("https:")) {
                https.add(u.replace("%p", Integer.toString(httpsPort())));
            } else {
                http.add(u.replace("%p", Integer.toString(httpPort())));
            }
        }
        add(name, "", http, result);
        add(name, " SSL", https, result);
        return result;
    }

    private static void add(String nameBase, String nameSuffix, List<String> all, Map<String, String> result) {
        String name;
        int no;

        no = 0;
        for (String u : all) {
            if (all.size() > 1) {
                no++;
                name = nameBase + "-" + no;
            } else {
                name = nameBase;
            }
            name = name + nameSuffix;
            result.put(name, u);
        }
    }
}
