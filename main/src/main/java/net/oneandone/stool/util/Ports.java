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

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Manage ports used for one stage. Immutable. Do not create directly, use Pool class instead. */
public class Ports {
    public static Ports forVhosts(List<Vhost> vhosts) {
        String id;
        int jmxDebug;

        if (vhosts.size() != 2) {
            throw new IllegalStateException(vhosts.toString());
        }
        id = null;
        for (Vhost v : vhosts) {
            if (id == null) {
                id = v.id;
            } else {
                if (!id.equals(v.id)) {
                    throw new IllegalArgumentException(id + " vs " + v.id);
                }
            }
        }
        jmxDebug = vhosts.get(indexOf(vhosts, JMX_DEBUG)).even;
        return new Ports(jmxDebug, jmxDebug + 1, webapp(vhosts));
    }

    private static Vhost webapp(List<Vhost> vhosts) {
        Vhost result;

        result = null;
        for (Vhost v : vhosts) {
            if (v.isWebapp()) {
                if (result != null) {
                    throw new IllegalStateException();
                }
                result = v;
            }
        }
        if (result == null) {
            throw new IllegalStateException(result.toString());
        }
        return result;
    }

    private static int indexOf(List<Vhost> vhosts, String name) {
        Vhost vhost;

        for (int i = 0; i < vhosts.size(); i++) {
            vhost = vhosts.get(i);
            if (name.equals(vhost.name)) {
                return i;
            }
        }
        return -1;
    }

    //--

    public static final String JMX_DEBUG = "+jmx+debug";

    public final int jmx;
    public final int debug;
    private final Vhost webapp;

    public Ports(int jmx, int debug, Vhost webapp) {
        this.jmx = jmx;
        this.debug = debug;
        this.webapp = webapp;
    }

    public Vhost webapp() {
        return webapp;
    }

    public Map<String, String> urlMap(String stageName, String hostname, String url) {
        Map<String, String> result;

        result = new LinkedHashMap<>();
        result.putAll(webapp().urlMap(stageName, hostname, url));
        return result;
    }

    public Map<Integer, Integer> dockerMap() {
        Map<Integer, Integer> result;

        result = new HashMap<>();
        result.put(jmx, jmx);
        result.put(debug, debug);
        result.put(8080, webapp.even);
        result.put(8443, webapp.even + 1);
        return result;
    }
}
