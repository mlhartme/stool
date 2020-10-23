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
    public enum Port {
        JMXMP;

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
        return new Ports(Port.JMXMP.get(labels));
    }

    //--

    public final int jmxmp;

    public Ports(int jmxmp) {
        this.jmxmp = jmxmp;
    }

    public boolean contains(int port) {
        return jmxmp == port;
    }

    public String toString() {
        return "Ports(jmxmp=" + jmxmp + ")";
    }

    public int hashCode() {
        return jmxmp;
    }

    public boolean equals(Object obj) {
        Ports ports;

        if (obj instanceof Ports) {
            ports = (Ports) obj;
            return jmxmp == ports.jmxmp;
        }
        return false;
    }

}
