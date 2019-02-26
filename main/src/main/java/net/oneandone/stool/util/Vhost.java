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

import net.oneandone.sushi.fs.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Represents one line in the "ports" file. Immutable */
public class Vhost {
    private static final char SEP = ' ';

    // parses   <even> <name> <id> [<docroot>]
    // where name is the application name.
    public static Vhost forLine(World world, String line) {
        int afterEven;
        int afterName;
        int afterId;
        int even;
        String name;
        String id;
        boolean webapp;

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

        afterId = line.indexOf(SEP, afterName + 1);
        if (afterId == -1) {
            id = line.substring(afterName + 1);
            webapp = false;
        } else {
            id = line.substring(afterName + 1, afterId);
            webapp = true;
        }
        return new Vhost(even, name, id, webapp);
    }

    public final int even;

    /** name of vhost, i.e. the application */
    public final String name;

    /** stage id */
    public final String id;

    public final boolean webapp;

    public Vhost(int even, String name, String id, boolean webapp) {
        if (name.indexOf(SEP) != -1) {
            throw new IllegalArgumentException(name);
        }
        if (id.indexOf('.') == -1) {
            throw new IllegalArgumentException(id);
        }
        this.even = even;
        this.name = name;
        this.id = id;
        this.webapp = webapp;
    }

    public boolean isWebapp() {
        return webapp;
    }

    public int httpPort() {
        return even;
    }

    public int httpsPort() {
        return even + 1;
    }

    public String httpsUrl(boolean vhosts, String stageName, String hostname) {
        return "https://" + (fqdn(vhosts, stageName, hostname) + ":" + httpsPort());
    }

    public String fqdn(boolean vhosts, String stageName, String hostname) {
        if (vhosts) {
            return name + "." + stageName + "." + hostname;
        } else {
            return hostname;
        }
    }

    public String toLine() {
        // CAUTION: just
        //    even + SEP
        // is an integer addition!
        return Integer.toString(even) + SEP + name + SEP + id + (webapp ? SEP + "webapp" : "");
    }

    public String toString() {
        return toLine();
    }

    /** null if not modified */
    public Vhost set(Integer newEven, boolean newWebapp) {
        if (Objects.equals(this.webapp, newWebapp) && (newEven == null || newEven == even)) {
            return null;
        }
        return new Vhost(newEven == null ? even : newEven, name, id, newWebapp);
    }

    public String context(String stageName, String hostname, String url) {
        String result;
        String context;

        result = null;
        for (String str : doMap(stageName, hostname, url).values()) {
            context = getContext(str);
            if (result == null) {
                result = context;
            } else if (!result.equals(context)) {
                throw new IllegalStateException("ambiguous context: " + result + " vs " + context);
            }
        }
        if (result == null) {
            throw new IllegalStateException("context not found: " + url);
        }
        if (!result.isEmpty() && !result.startsWith("/")) {
            throw new IllegalStateException(hostname + " " + url + " " + result);
        }
        return result;
    }

    public Map<String, String> urlMap(String stageName, String hostname, String url) {
        Map<String, String> result;

        result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : doMap(stageName, hostname, url).entrySet()) {
            result.put(entry.getKey(), hideContext(entry.getValue()));
        }
        return result;
    }

    private static String hideContext(String url) {
        int beforeHost;
        int afterHost;
        int context;

        beforeHost = url.indexOf("://");
        if (beforeHost == -1) {
            return url;
        }
        afterHost = url.indexOf("/", beforeHost + 3);
        if (afterHost == -1) {
            return url;
        }
        context = url.indexOf("//", afterHost + 1);
        if (context == -1) {
            return url;
        }
        return url.substring(0, context) + url.substring(context + 1);
    }

    /** return path as used in Tomcat context element - either empty of starts with a slash */
    private static String getContext(String url) {
        int beforeHost;
        int afterHost;
        int context;

        beforeHost = url.indexOf("://");
        if (beforeHost == -1) {
            return "";
        }
        afterHost = url.indexOf("/", beforeHost + 3);
        if (afterHost == -1) {
            return "";
        }
        context = url.indexOf("//", afterHost + 1);
        return context == -1 ? "" : url.substring(afterHost, context);
    }

    private Map<String, String> doMap(String stageName, String hostname, String url) {
        Map<String, String> result;
        Map<Character, String> map;
        List<String> all;
        List<String> http;
        List<String> https;

        result = new LinkedHashMap<>();
        map = new HashMap<>();
        map.put('h', hostname);
        map.put('a', name);
        map.put('s', stageName);
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
