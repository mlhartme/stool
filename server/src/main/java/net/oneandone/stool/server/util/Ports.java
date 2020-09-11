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

import java.util.Map;

/** Manage ports used for one stage. Immutable. Do not create directly, use Pool class instead. */
public class Ports {
    public static final int HTTP = 80;
    public static final int HTTPS = 443;

    public enum Port {
        HTTP, HTTPS, JMXMP;

        public String label() {
            return ImageInfo.IMAGE_LABEL_PORT_PREFIX + toString().toLowerCase();
        }

        public int get(Map<String, String> labels) {
            String str;

            str = labels.get(label());
            return str == null ? -1 : Integer.parseInt(str);
        }
    }

    public static Ports fromDeclaredLabels(Map<String, String> labels) {
        return new Ports(Port.HTTP.get(labels), Port.HTTPS.get(labels), Port.JMXMP.get(labels));
    }

    //--

    public final int http;
    public final int https;
    public final int jmxmp;

    public Ports(int http, int https, int jmxmp) {
        this.http = http;
        this.https = https;
        this.jmxmp = jmxmp;
    }

    public boolean contains(int port) {
        return http == port || https == port || jmxmp == port;
    }

    public String toString() {
        return "Ports(http=" + http + ", https=" + https + ", jmxmp=" + jmxmp + ")";
    }

    public int hashCode() {
        return http ^ https ^ jmxmp;
    }

    public boolean equals(Object obj) {
        Ports ports;

        if (obj instanceof Ports) {
            ports = (Ports) obj;
            return http == ports.http && https == ports.https && jmxmp == ports.jmxmp;
        }
        return false;
    }

}
