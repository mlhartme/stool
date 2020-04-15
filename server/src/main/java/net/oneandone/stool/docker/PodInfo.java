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
package net.oneandone.stool.docker;

import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Pod;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.sushi.util.Strings;

import java.util.List;
import java.util.Map;

public class PodInfo {
    public static PodInfo create(V1Pod pod) {
        return new PodInfo(pod.getMetadata().getName(), pod.getStatus().getPhase(), containerId(pod),
                pod.getMetadata().getLabels());
    }

    private static String containerId(V1Pod pod) {
        List<V1ContainerStatus> lst;
        String result;

        lst = pod.getStatus().getContainerStatuses();
        if (lst == null) {
            return null;
        }
        if (lst.size() != 1) {
            throw new IllegalStateException("single container expected, got " + lst);
        }
        result = lst.get(0).getContainerID();
        return result == null ? null : Strings.removeLeft(result, "docker://");
    }

    public final String name;
    public final String phase;
    public final String containerId;
    public final Map<String, String> labels;

    public PodInfo(String name, String phase, String containerId, Map<String, String> labels) {
        this.name = name;
        this.phase = phase;
        this.containerId = containerId;
        this.labels = labels;
    }

    public String repositoryTag() {
        return Engine.decodeLabel(labels.get(Stage.POD_LABEL_REPOSITORY_TAG));
    }

    public boolean isRunning() {
        return "Running".equals(phase);
    }
}
