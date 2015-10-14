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
    private final List<Vhost> vhosts;

    public Ports(List<Vhost> vhosts) {
        if (vhosts.size() < 2) {
            throw new IllegalArgumentException();
        }
        this.vhosts = vhosts;
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
