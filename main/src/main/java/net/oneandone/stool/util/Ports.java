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
        Vhost first;
        Vhost second;

        if (vhosts.size() != 2) {
            throw new IllegalStateException(vhosts.toString());
        }
        first = vhosts.get(0);
        second = vhosts.get(1);
        if (!first.id.equals(second.id)) {
            throw new IllegalArgumentException(first.id + " vs " + second.id);
        }
        if (!first.app.equals(second.app)) {
            throw new IllegalArgumentException(first.app + " vs " + second.app);
        }
        if (first.webapp == second.webapp) {
            throw new IllegalArgumentException(first.app + " vs " + second.app);
        }
        if (first.webapp) {
            return forVhosts(first, second);
        } else {
            return forVhosts(second, first);
        }
    }

    public static Ports forVhosts(Vhost webapp, Vhost jmxDebug) {
        return new Ports(webapp.httpPort(), webapp.httpsPort(), jmxDebug.even, jmxDebug.even + 1, webapp);
    }

    //--

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
