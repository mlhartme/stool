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

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Pod;
import net.oneandone.sushi.util.Strings;

import java.util.List;
import java.util.Map;

/** Represents a pod as used by Stool. In particular, the pod has a single container */
public class PodInfo {
    public static PodInfo create(V1Pod pod) {
        V1ContainerStatus status;
        String containerId;
        String repositoryTag;
        V1Container container;

        container = container(pod);
        repositoryTag = container != null ? container.getImage() : null;
        status = status(pod);
        // CAUTION:
        // status also contains an imageId which seems ok in most cases. However, I've seen obscure ids, maybe
        // prefixed by docker-pullable://, so I can't rely on it
        if (status != null) {
            containerId = pruneDocker(status.getContainerID());
        } else {
            containerId = null;
        }
        return new PodInfo(pod.getMetadata().getName(), pod.getStatus().getPhase(), pod.getStatus().getPodIP(),
                repositoryTag, containerId, pod.getMetadata().getLabels());
    }

    private static String pruneDocker(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        } else {
            return Strings.removeLeft(id, "docker://");
        }
    }

    private static V1Container container(V1Pod pod) {
        List<V1Container> lst;

        lst = pod.getSpec().getContainers();
        if (lst == null) {
            return null;
        }
        if (lst.size() != 1) {
            throw new IllegalStateException("single container expected, got " + lst);
        }
        return lst.get(0);
    }

    private static V1ContainerStatus status(V1Pod pod) {
        List<V1ContainerStatus> lst;

        lst = pod.getStatus().getContainerStatuses();
        if (lst == null) {
            return null;
        }
        if (lst.size() != 1) {
            throw new IllegalStateException("single container expected, got " + lst);
        }
        return lst.get(0);
    }

    public final String name;
    public final String phase;
    public final String ip;
    public final String repositoryTag;
    public final String containerId;
    public final Map<String, String> labels;

    public PodInfo(String name, String phase, String ip, String repositoryTag, String containerId, Map<String, String> labels) {
        this.name = name;
        this.phase = phase;
        this.ip = ip;
        this.repositoryTag = repositoryTag;
        this.containerId = containerId;
        this.labels = labels;
    }

    public boolean isRunning() {
        return "Running".equals(phase);
    }

    public String toString() {
        return name + ":" + repositoryTag + " " + containerId + " " + phase + " " + ip;
    }
}
