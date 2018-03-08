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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Manage ports used for one stage. Immutable. Do not create directly, use Pool class instead. */
public class Ports {
    public static final String JMX_DEBUG = "+jmx+debug";

    private final List<Vhost> vhosts;
    private final int jmxDebug;

    public Ports(List<Vhost> vhosts) {
        String id;

        if (vhosts.isEmpty()) {
            throw new IllegalStateException();
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
        this.vhosts = vhosts;
        this.jmxDebug = indexOf(JMX_DEBUG);
        if (jmxDebug == -1) {
            throw new IllegalArgumentException(vhosts.toString());
        }
    }

    public int jmx() {
        return vhosts.get(jmxDebug).even;
    }

    public int debug() {
        return vhosts.get(jmxDebug).even + 1;
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

    public Vhost lookup(String name) {
        int idx;

        idx = indexOf(name);
        return idx == -1 ? null : vhosts.get(idx);
    }

    private int indexOf(String name) {
        Vhost vhost;

        for (int i = 0; i < vhosts.size(); i++) {
            vhost = vhosts.get(i);
            if (name.equals(vhost.name)) {
                return i;
            }
        }
        return -1;
    }

    public Map<String, String> urlMap(String stageName, String hostname, String url) {
        Map<String, String> result;

        result = new LinkedHashMap<>();
        for (Vhost vhost : vhosts()) {
            if (vhost.isWebapp()) {
                result.putAll(vhost.urlMap(stageName, hostname, url));
            }
        }
        return result;
    }

    public Map<Integer, Integer> dockerMap() {
        Map<Integer, Integer> result;

        result = new HashMap<>();
        for (Vhost vhost : vhosts) {
            result.put(vhost.even, vhost.even);
            result.put(vhost.even + 1, vhost.even + 1);
        }
        result.put(jmx(), jmx());
        result.put(debug(), debug());
        return result;
    }
}
