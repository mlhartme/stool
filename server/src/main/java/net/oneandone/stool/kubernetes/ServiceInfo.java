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

import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;

import java.util.List;
import java.util.Map;

public class ServiceInfo {
    public static ServiceInfo create(V1Service service) {
        String name;
        List<V1ServicePort> ports;

        name = service.getMetadata().getName();
        ports = service.getSpec().getPorts();
        if (ports.size() != 1) {
            throw new IllegalStateException(ports.toString());
        }
        return new ServiceInfo(name, service.getSpec().getClusterIP(), ports.get(0).getNodePort(), ports.get(0).getPort(),
                service.getMetadata().getLabels());
    }

    public final String name;
    public final String clusterIp;
    public final int nodePort;
    public final int containerPort;
    public final Map<String, String> labels;

    public ServiceInfo(String name, String clusterIp, int nodePort, int containerPort, Map<String, String> labels) {
        this.name = name;
        this.clusterIp = clusterIp;
        this.nodePort = nodePort;
        this.containerPort = containerPort;
        this.labels = labels;
    }
}
