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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.oneandone.stool.stage.Stage;

import java.util.List;

/** Manage ports used for one stage. Immutable. Do not create directly, use Pool class instead. */
public class Ports {
    public static Ports fromLabels(JsonObject labels) {
        return new Ports(
                get(labels, Stage.LABEL_PORT_HTTP),
                get(labels, Stage.LABEL_PORT_HTTPS),
                get(labels, Stage.LABEL_PORT_JMXMP),
                get(labels, Stage.LABEL_PORT_DEBUG),
                null);
    }

    private static int get(JsonObject labels, String name) {
        JsonElement str;

        str = labels.get(name);
        return str == null ? -1 : Integer.parseInt(str.getAsString());
    }

    public static Ports forVhosts(List<Vhost> vhosts) {
        String id;
        int jmxDebug;
        Vhost webapp;

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
        webapp = webapp(vhosts);
        return new Ports(webapp.httpPort(), webapp.httpsPort(), jmxDebug, jmxDebug + 1, webapp);
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

    public final int http;
    public final int https;
    public final int jmx;
    public final int debug;

    private final Vhost webapp; // TODO: dump

    public Ports(int http, int https, int jmx, int debug, Vhost webapp) {
        this.http = http;
        this.https = https;
        this.jmx = jmx;
        this.debug = debug;
        this.webapp = webapp;
    }

    public Vhost webapp() {
        return webapp;
    }
}
