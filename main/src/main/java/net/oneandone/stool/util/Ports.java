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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Manage ports used for one stage. Immutable. Do not create directly, use Pool class instead. */
public class Ports {
    public static final String STOP_WRAPPER = "+stop+wrapper";
    public static final String JMX_DEBUG = "+jmx+debug";

    private final List<Vhost> vhosts;
    private final int stopWrapper;
    private final int jmxDebug;

    public Ports(List<Vhost> vhosts) {
        String stage;

        if (vhosts.isEmpty()) {
            throw new IllegalStateException();
        }
        stage = null;
        for (Vhost v : vhosts) {
            if (stage == null) {
                stage = v.stage;
            } else {
                if (!stage.equals(v.stage)) {
                    throw new IllegalArgumentException(stage + " vs " + v.stage);
                }
            }
        }
        this.vhosts = vhosts;
        this.stopWrapper = indexOf(STOP_WRAPPER);
        this.jmxDebug = indexOf(JMX_DEBUG);
        if (stopWrapper == -1) {
            throw new IllegalArgumentException(vhosts.toString());
        }
        if (jmxDebug == -1) {
            throw new IllegalArgumentException(vhosts.toString());
        }
    }

    public int stop() {
        return vhosts.get(stopWrapper).even;
    }

    public int wrapper() {
        return vhosts.get(stopWrapper).even + 1;
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
}
