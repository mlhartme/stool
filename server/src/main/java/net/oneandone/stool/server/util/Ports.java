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
package net.oneandone.stool.server.util;

import net.oneandone.stool.docker.ImageInfo;
import net.oneandone.stool.server.stage.Stage;

import java.util.HashMap;
import java.util.Map;

/** Manage ports used for one stage. Immutable. Do not create directly, use Pool class instead. */
public class Ports {
    public enum Port {
        HTTP, HTTPS, JMXMP, DEBUG;

        public int get(Map<String, String> labels, String prefix) {
            String str;

            str = labels.get(prefix + toString().toLowerCase());
            return str == null ? -1 : Integer.parseInt(str);
        }

        public void add(Map<String, String> dest, String prefix, int value) {
            if (value != -1) {
                dest.put(prefix + toString().toLowerCase(), Integer.toString(value));
            }
        }
    }

    public static Ports fromUsedLabels(Map<String, String> labels) {
        return fromLabels(labels, Stage.POD_LABEL_PORT_USED_PREFIX);
    }

    public static Ports fromDeclaredLabels(Map<String, String> labels) {
        return fromLabels(labels, ImageInfo.IMAGE_LABEL_PORT_DECLARED_PREFIX);
    }

    private static Ports fromLabels(Map<String, String> labels, String prefix) {
        return new Ports(
                Port.HTTP.get(labels, prefix),
                Port.HTTPS.get(labels, prefix),
                Port.JMXMP.get(labels, prefix),
                Port.DEBUG.get(labels, prefix));
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

    public boolean contains(int port) {
        return http == port || https == port || jmxmp == port || debug == port;
    }

    public String toString() {
        return "Ports(http=" + http + ", https=" + https + ", jmxmp=" + jmxmp + ", debug=" + debug + ")";
    }

    public int hashCode() {
        return http ^ https ^ jmxmp ^ debug;
    }

    public boolean equals(Object obj) {
        Ports ports;

        if (obj instanceof Ports) {
            ports = (Ports) obj;
            return http == ports.http && https == ports.https && jmxmp == ports.jmxmp && debug == ports.debug;
        }
        return false;
    }

    public Map<String, String> toUsedLabels() {
        return toLabels(Stage.POD_LABEL_PORT_USED_PREFIX);
    }

    private Map<String, String> toLabels(String prefix) {
        Map<String, String> result;

        result = new HashMap<>();
        Port.HTTP.add(result, prefix, http);
        Port.HTTPS.add(result, prefix, https);
        Port.JMXMP.add(result, prefix, jmxmp);
        Port.DEBUG.add(result, prefix, debug);
        return result;
    }

}
