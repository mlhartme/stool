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

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.api.model.RouteSpecBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;

import java.util.ArrayList;
import java.util.List;

public class OpenShift implements AutoCloseable {
    private final OpenShiftClient client;
    private final String namespace;

    public static OpenShift create(String context) {
        Config config;

        config = Config.autoConfigure(context);
        return new OpenShift(new DefaultOpenShiftClient(config));
    }

    public static OpenShift create() {
        return new OpenShift(new DefaultOpenShiftClient());
    }

    private OpenShift(OpenShiftClient client) {
        this.client = client;
        this.namespace = client.getNamespace();
    }

    @Override
    public void close() {
        client.close();
    }

    public void routeCreate(String name, String host, String serviceName, boolean tlsPassthrough, String targetPort) {
        RouteSpecBuilder spec;
        RouteBuilder route;

        spec = new RouteSpecBuilder()
                .withHost(host)
                .withNewTo().withKind("Service").withName(serviceName).endTo()
                .withNewPort().withTargetPort(new IntOrString(targetPort)).endPort();
        if (tlsPassthrough) {
            spec.withNewTls().withTermination("passthrough").endTls();
        } else {
            spec.withPath("/");
        }
        route = new RouteBuilder()
                .withNewMetadata().withNamespace(namespace).withName(name).endMetadata()
                .withSpec(spec.build());
        client.routes().inNamespace(namespace).create(route.build());
    }

    public void routeDelete(String name) {
        Route route;

        route = new RouteBuilder()
                .withNewMetadata().withNamespace(namespace).withName(name).endMetadata()
                .build();
        client.routes().inNamespace(namespace).delete(route);
    }

    public List<String> routeList() {
        List<String> result;

        result = new ArrayList<>();
        for (Route route : client.routes().inNamespace(namespace).list().getItems()) {
            result.add(route.getMetadata().getName());
        }
        return result;
    }
}
