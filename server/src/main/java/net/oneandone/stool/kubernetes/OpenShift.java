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
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.PolicyRuleBuilder;
import io.fabric8.openshift.api.model.RoleBindingBuilder;
import io.fabric8.openshift.api.model.RoleBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.api.model.RouteSpecBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OpenShift implements AutoCloseable {
    private final OpenShiftClient client;
    private final String namespace;

    public static void main(String[] args) throws IOException {
        try (OpenShift os = create("test-pearl")) {
            os.createServiceAccount("mhm");
            os.createRole("r1");
            os.createBinding("binder", "mhm", "r1");
            System.out.println("created");
            os.deleteBinding("binder");
            os.deleteRole("r1");
            os.deleteServiceAccount("mhm");
        }
        System.out.println("done");
    }

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

    public Stats statsOpt(String pod) {
        PodMetrics p;
        List<ContainerMetrics> lst;
        Map<String, Quantity> usage;

        try {
            p = client.top().pods().metrics(namespace, pod);
        } catch (KubernetesClientException e) {
            if (e.getCode() == 404) {
                // TODO: could be stats not found or pod not found ...
                return null;
            } else {
                throw e;
            }
        }
        lst = p.getContainers();
        if (lst.size() != 1) {
            throw new IllegalStateException();
        }
        usage = lst.get(0).getUsage();
        return new Stats(usage.get("cpu").toString(), usage.get("memory").toString());
    }

    //--

    public void createServiceAccount(String name) {
        ServiceAccountBuilder sa;

        sa = new ServiceAccountBuilder().withNewMetadata().withNamespace(namespace).withName(name).endMetadata();
        client.serviceAccounts().create(sa.build());
    }

    public List<String> getServiceAccountSecrets(String name) {
        return null;
    }

    public void deleteServiceAccount(String name) throws IOException {
        if (!client.serviceAccounts().inNamespace(namespace).withName(name).delete()) {
            throw new IOException("delete failed: " + name);
        }
    }

    public void createRole(String name, String... pods) {
        PolicyRuleBuilder ruleBuilder;
        RoleBuilder rb;

        ruleBuilder = new PolicyRuleBuilder();
        ruleBuilder.withApiGroups("").withAttributeRestrictions(null).withResources("pods").withResourceNames(pods).withVerbs("get");
        rb = new RoleBuilder();
        rb.withNewMetadata().withName(name).endMetadata().withRules(ruleBuilder.build());
        client.roles().create(rb.build());
    }

    public void deleteRole(String name) throws IOException {
        if (!client.roles().inNamespace(namespace).withName(name).delete()) {
            throw new IOException("delete failed: " + name);
        }
    }

    public void createBinding(String name, String serviceAccount, String role) {
        RoleBindingBuilder rb;
        ObjectReferenceBuilder s;

        s = new ObjectReferenceBuilder();
        s.withKind("ServiceAccount");
        s.withName(serviceAccount);
        rb = new RoleBindingBuilder();
        rb.withNewMetadata().withName(name).endMetadata()
                .withNewRoleRef().withName(role).withNamespace(namespace).endRoleRef()
                .withSubjects(s.build());
        client.roleBindings().create(rb.build());
    }

    public void deleteBinding(String name) throws IOException {
        if (!client.roleBindings().inNamespace(namespace).withName(name).delete()) {
            throw new IOException("delete failed: " + name);
        }
    }
}
