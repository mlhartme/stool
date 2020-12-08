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

import io.kubernetes.client.openapi.models.V1Pod;

import java.util.Map;

/** Represents a pod as used by Stool. In particular, the pod has a single container */
public class PodInfo {
    public static PodInfo create(V1Pod pod) {
        return new PodInfo(pod.getMetadata().getName(), pod.getStatus().getPhase(), pod.getStatus().getPodIP(), pod.getMetadata().getLabels(),
                ContainerInfo.createMap(pod));
    }

    public final String name;
    public final String phase;
    public final String ip;
    public final Map<String, String> labels;
    public final Map<String, ContainerInfo> containers;

    public PodInfo(String name, String phase, String ip, Map<String, String> labels, Map<String, ContainerInfo> containers) {
        this.name = name;
        this.phase = phase;
        this.ip = ip;
        this.labels = labels;
        this.containers = containers;
    }

    public String repositoryTag(String containerName) {
        ContainerInfo info;

        info = containers.get(containerName);
        return info == null ? null : info.image;
    }

    public boolean isRunning() {
        return "Running".equals(phase);
    }

    public String toString() {
        return name + ":" + phase + " " + ip + " " + labels + " " + containers;
    }
}
