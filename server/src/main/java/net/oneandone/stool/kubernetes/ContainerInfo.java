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
package net.oneandone.stool.kubernetes;

import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Pod;
import net.oneandone.sushi.util.Strings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Represents a container used by Stool. In particular, the pod has a single container */
public class ContainerInfo {
    public static Map<String, ContainerInfo> createMap(V1Pod pod) {
        List<V1ContainerStatus> lst;
        Map<String, ContainerInfo> result;
        String name;

        lst = pod.getStatus().getContainerStatuses();
        result = new HashMap<>();
        if (lst != null) {
            for (V1ContainerStatus status : lst) {
                name = status.getName();
                result.put(name, new ContainerInfo(name, status.getImage(), pruneDocker(status.getContainerID()), status.getReady()));
            }
        }
        return result;
    }

    private static String pruneDocker(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        } else {
            return Strings.removeLeft(id, "docker://");
        }
    }

    public final String name;
    public final String image;
    public final String id;
    public final boolean ready;

    public ContainerInfo(String name, String image, String id, boolean ready) {
        this.name = name;
        this.image = image;
        this.id = id;
        this.ready = ready;
    }

    public String toString() {
        return name + ":" + image + " " + id;
    }
}
