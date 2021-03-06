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

import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;

import java.util.HashMap;
import java.util.Map;

/** Represents a pod as used by Stool. In particular, the pod has a single container */
public class DeploymentInfo {
    public static DeploymentInfo create(Deployment deployment) {
        DeploymentStatus status;
        Map<String, String> labels;
        Integer statusAvailable;
        LabelSelector selector;

        if (deployment.getMetadata() == null) {
            throw new IllegalStateException("not metadata");
        }
        labels = deployment.getMetadata().getLabels();
        status = deployment.getStatus();
        statusAvailable = status == null ? null : status.getAvailableReplicas();
        selector = deployment.getSpec().getSelector();
        if (selector == null) {
            throw new IllegalStateException("" + selector);
        }
        if (selector.getMatchExpressions() != null && !selector.getMatchExpressions().isEmpty()) {
            throw new IllegalStateException("" + selector);
        }
        if (selector.getMatchLabels() == null || selector.getMatchLabels().isEmpty()) {
            throw new IllegalStateException("" + selector);
        }
        return new DeploymentInfo(deployment.getMetadata().getName(),
                labels == null ? new HashMap<>() : labels,
                deployment.getSpec().getReplicas(),
                statusAvailable == null ? 0 : statusAvailable,
                selector.getMatchLabels());
    }

    public final String name;
    public final Map<String, String> labels;
    public final int specReplicas;
    public final int statusAvailable;
    public final Map<String, String> selector;

    public DeploymentInfo(String name, Map<String, String> labels, int specReplicas, int statusAvailable, Map<String, String> selector) {
        this.name = name;
        this.labels = labels;
        this.specReplicas = specReplicas;
        this.statusAvailable = statusAvailable;
        this.selector = selector;
    }
}
