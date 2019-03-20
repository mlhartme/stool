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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Manage ports used for one stage. Immutable. Do not create directly, use Pool class instead. */
public class Ports {
    public static Ports fromContainerLabels(JsonObject labels) {
        return fromLabels(labels, Stage.LABEL_CONTAINER_PORT_PREFIX);
    }

    public static Ports fromHostLabels(JsonObject labels) {
        return fromLabels(labels, Stage.LABEL_HOST_PORT_PREFIX);
    }

    public static Ports fromLabels(JsonObject labels, String prefix) {
        return new Ports(
                get(labels, prefix + Stage.LABEL_HTTP_SUFFIX),
                get(labels, prefix + Stage.LABEL_HTTPS_SUFFIX),
                get(labels, prefix + Stage.LABEL_JMXMP_SUFFIX),
                get(labels, prefix + Stage.LABEL_DEBUG_SUFFIX));
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
        return new Ports(webapp.httpPort(), webapp.httpsPort(), jmxDebug.even, jmxDebug.even + 1);
    }


    public Map<Integer, Integer> map(Ports hostPorts) {
        Map<Integer, Integer> result;

        result = new HashMap<>();
        addOpt(result, http, hostPorts.http);
        addOpt(result, https, hostPorts.https);
        addOpt(result, jmxmp, hostPorts.jmxmp);
        addOpt(result, debug, hostPorts.debug);
        return result;
    }

    private static void addOpt(Map<Integer, Integer> dest, int left, int right) {
        if (right == -1) {
            throw new IllegalStateException();
        }
        if (left != -1) {
            dest.put(left, right);
        }
    }

    //--

    public final int http;
    public final int https;
    public final int jmxmp;
    public final int debug;

    public Ports(int http, int https, int jmxmp, int debug) {
        this.http = http;
        this.https = https;
        this.jmxmp = jmxmp;
        this.debug = debug;
    }

    public Map<String, String> toHostLabels() {
        return toLabels(Stage.LABEL_HOST_PORT_PREFIX);
    }

    private Map<String, String> toLabels(String prefix) {
        Map<String, String> result;

        result = new HashMap<>();
        addOpt(result, prefix + Stage.LABEL_HTTP_SUFFIX, http);
        addOpt(result, prefix + Stage.LABEL_HTTPS_SUFFIX, https);
        addOpt(result, prefix + Stage.LABEL_JMXMP_SUFFIX, jmxmp);
        addOpt(result, prefix + Stage.LABEL_DEBUG_SUFFIX, debug);
        return result;
    }

    private static void addOpt(Map<String, String> dest, String key, int value) {
        if (value != -1) {
            dest.put(key, Integer.toString(value));
        }
    }
}
