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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.openshift.api.model.PolicyRuleBuilder;
import io.fabric8.openshift.api.model.RoleBindingBuilder;
import io.fabric8.openshift.api.model.RoleBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.api.model.RouteSpecBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import net.oneandone.stool.client.PodConfig;
import net.oneandone.sushi.util.Strings;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class Engine implements AutoCloseable {
    public static Engine createCluster() {
        return createClusterOrLocal(null);
    }

    public static Engine createLocal(String context) {
        return createClusterOrLocal(context);
    }

    public static Engine createClusterOrLocal(String contextOpt) {
        if (contextOpt == null) {
            return new Engine(new DefaultOpenShiftClient());
        } else {
            Config config;

            config = Config.autoConfigure(contextOpt);
            return new Engine(new DefaultOpenShiftClient(config));
        }
    }


    public static Engine create(PodConfig config) {
        return create(config.server, config.namespace, config.token);
    }

    public static Engine create(String masterUrl, String namespace, String token) {
        Config config;
        String old;

        // TODO: not thread safe ...
        old = System.getProperty(Config.KUBERNETES_DISABLE_AUTO_CONFIG_SYSTEM_PROPERTY);
        System.setProperty(Config.KUBERNETES_DISABLE_AUTO_CONFIG_SYSTEM_PROPERTY, "true");
        config = new ConfigBuilder().withMasterUrl(masterUrl).withTrustCerts(true).withNamespace(namespace).withOauthToken(token).build();
        if (old == null) {
            System.getProperties().remove(Config.KUBERNETES_DISABLE_AUTO_CONFIG_SYSTEM_PROPERTY);
        } else {
            System.setProperty(Config.KUBERNETES_DISABLE_AUTO_CONFIG_SYSTEM_PROPERTY, old);
        }

        return new Engine(new DefaultOpenShiftClient(config));
    }

    private final OpenShiftClient client;
    private final String namespace;

    private Engine(OpenShiftClient client) {
        this.client = client;
        this.namespace = client.getNamespace();
    }

    public boolean isOpenShift() throws IOException {
        try {
            client.builds().list();
            return true;
        } catch (KubernetesClientException e) {
            if (e.getCode() == 404) {
                return false;
            } else {
                throw new IOException("cannot detect openshift", e);
            }
        }
    }

    public String getNamespace() {
        return namespace;
    }

    public void close() {
        client.close();
    }

    //-- namespace

    public void namespaceReset() throws IOException {
        for (DeploymentInfo deployment : deploymentList().values()) {
            System.out.println("delete deployment: " + deployment.name);
            deploymentDelete(deployment.name);
        }
        for (PodInfo pod: podList().values()) {
            System.out.println("delete pod: " + pod.name);
            podDelete(pod.name);
        }
    }


    //-- deployments

    public Map<String, DeploymentInfo> deploymentList() throws IOException {
        Map<String, DeploymentInfo> result;
        String name;

        try {
            result = new LinkedHashMap<>();
            for (Deployment deployment : client.apps().deployments().inNamespace(namespace).list().getItems()) {
                name = deployment.getMetadata().getName();
                result.put(name, DeploymentInfo.create(deployment));
            }
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
        return result;
    }

    public DeploymentInfo deploymentProbe(String name) throws IOException {
        Deployment d;

        try {
            d = client.apps().deployments().inNamespace(namespace).withName(name).get();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
        return d == null ? null : DeploymentInfo.create(d);
    }

    /** @return true for 0 spec replicas */
    public DeploymentInfo deploymentAwaitAvailable(String name) throws IOException {
        DeploymentInfo info;
        int count;

        count = 0;
        while (true) {
            info = deploymentProbe(name);
            if (info.specReplicas == 0 || info.statusAvailable > 0) {
                return info;
            }
            count++;
            if (count > 500) {
                throw new IOException(name + ": waiting for available replicas timed out");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new IOException(name + "waiting for replicas interrupted", e);
            }
        }
    }

    public void deploymentAwaitGone(String name) throws IOException {
        DeploymentInfo info;
        int count;

        count = 0;
        while (true) {
            info = deploymentProbe(name);
            if (info == null) {
                return;
            }
            count++;
            if (count > 500) {
                throw new IOException(name + ": waiting for deployment to vanish timed out");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new IOException(name + "waiting for deployment to vanish interrupted", e);
            }
        }
    }

    public void deploymentCreate(String name, Map<String, String> selector, Map<String, String> deploymentLabels,
                                 String image, String[] command, String hostname, Map<String, String> podLabels) throws IOException {
        try {
            client.apps().deployments().inNamespace(namespace).create(deployment(name, selector, deploymentLabels, image, command, hostname, podLabels));
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
    }

    /** @return containerId or null */
    public void deploymentDelete(String name) throws IOException {
        try {
            client.apps().deployments().inNamespace(namespace).withName(name).delete();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private Deployment deployment(String name, Map<String, String> selector, Map<String, String> deploymentLabels,
                                    String image, String[] command, String hostname, Map<String, String> podLabels) {
        List<Container> cl;

        cl = new ArrayList<>();
        cl.add(container(image, command));
        return new DeploymentBuilder()
                .withNewMetadata()
                  .withName(name)
                  .withLabels(deploymentLabels)
                .endMetadata()
                .withNewSpec()
                  .withReplicas(1)
                  .withNewSelector().withMatchLabels(selector).endSelector()
                  .withNewTemplate()
                    .withNewMetadata().withLabels(podLabels).endMetadata()
                    .withNewSpec()
                      .withHostname(hostname)
                      .withContainers(cl)
                    .endSpec()
                  .endTemplate()
                .endSpec().build();
    }

    //--

    public static String labelSelector(Map<String, String> labelSelector) {
        StringBuilder builder;

        builder = new StringBuilder();
        for (Map.Entry<String, String> entry : labelSelector.entrySet()) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(entry.getKey());
            builder.append('=');
            builder.append(entry.getValue());
        }
        return builder.toString();
    }

    //-- pods

    public Map<String, PodInfo> podList() throws IOException {
        return podList(Strings.toMap());
    }

    public Map<String, PodInfo> podList(Map<String, String> labelSelector) throws IOException {
        PodList list;
        Map<String, PodInfo> result;
        String name;

        try {
            list = client.pods().inNamespace(namespace).withLabels(labelSelector).list();
            result = new LinkedHashMap<>();
            for (Pod pod : list.getItems()) {
                name = pod.getMetadata().getName();
                result.put(name, PodInfo.create(pod));
            }
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
        return result;
    }

    /** @return null if not found */
    public PodInfo podProbe(String name) throws IOException {
        Pod pod;

        try {
            pod = client.pods().inNamespace(namespace).withName(name).get();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
        return pod == null ? null : PodInfo.create(pod);
    }

    public boolean podCreate(String name, String image, String[] command, String hostname, boolean healing, Map<String, String> labels)
            throws IOException {
        String phase;

        try {
            client.pods().inNamespace(namespace).create(pod(name, image, command, hostname, healing, labels));
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }

        phase = podAwait(name, "Running", "Failed", "Succeeded");
        if (phase.equals("Failed")) {
            throw new IOException("create-pod failed: " + phase);
        }
        return "Running".equals(phase);
    }

    public void podDelete(String name) throws IOException {
        try {
            client.pods().inNamespace(namespace).withName(name).delete();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
        podAwait(name, null);
    }

    public boolean podContainerRunning(String podName, String containerName) throws IOException {
        ContainerStatus status;
        ContainerState state;

        status = getPodContainerStatus(podName, containerName);
        state = status.getState();
        if (state.getTerminated() != null) {
            return false;
        }
        if (state.getRunning() != null) {
            return true;
        }
        throw new IOException("unknown state: " + state);
    }

    private ContainerStatus getPodContainerStatus(String podName, String containerName) throws IOException {
        Pod pod;
        List<ContainerStatus> lst;

        try {
            pod = client.pods().inNamespace(namespace).withName(podName).get();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
        if (pod == null) {
            throw new FileNotFoundException("pod/" + podName);
        }
        lst = pod.getStatus().getContainerStatuses();
        for (ContainerStatus status : lst) {
            if (status.getName().equals(containerName)) {
                return status;
            }
        }
        throw new IllegalStateException(lst.toString());
    }

    public String podAwait(String name, String... expectedPhases) throws IOException {
        PodInfo info;
        int count;
        String phase;

        count = 0;
        while (true) {
            info = podProbe(name);
            phase = info == null ? null : info.phase;
            if (expectedPhases == null) {
                if (same(null, phase)) {
                    return null;
                }
            } else {
                for (String e : expectedPhases) {
                    if (same(e, phase)) {
                        return phase;
                    }
                }
            }
            count++;
            if (count > 500) {
                throw new IOException("waiting for phase '" + toString(expectedPhases)
                        + "' timed out, phase is now " + phase);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new IOException("waiting for phase '" + toString(expectedPhases) + "' interrupted", e);
            }
        }
    }

    public String podLogs(String pod) throws IOException {
        try {
            return client.pods().inNamespace(namespace).withName(pod).getLog();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
    }

    private static String toString(String[] args) {
        StringBuilder result;

        result = new StringBuilder();
        for (String arg : args) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(arg);
        }
        return result.toString();
    }

    private static Container container(String image, String... command) {
        Map<String, Quantity> limits;
        ContainerBuilder container;
        List<EnvVar> lst;

        limits = new HashMap<>();
        lst = new ArrayList<>();
        container = new ContainerBuilder();
        container.withNewResources().withLimits(limits).endResources()
                .withName("noname")
                .withImage(image)
                .withEnv(lst)
                .withImagePullPolicy("Never");

        if (command != null) {
            container.withCommand(command);
        }
        return container.build();
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private Pod pod(String name, String image, String[] command, String hostname, boolean healing, Map<String, String> labels) {
        return new PodBuilder()
                .withNewMetadata().withName(name).withLabels(labels).endMetadata()
                .withNewSpec()
                .withRestartPolicy(healing ? "Always" : "Never")
                .withHostname(hostname)
                .addAllToVolumes(new ArrayList<>())
                .addToContainers(container(image, command))
                .endSpec().build();
    }

    //-- helm

    public JsonObject helmRead(String name) throws IOException {
        List<Secret> lst;

        try {
            lst = client.secrets().inNamespace(namespace)
                    .withLabels(Strings.toMap("owner", "helm", "name", name, "status", "deployed")).list().getItems();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
        switch (lst.size()) {
            case 0:
                throw new java.io.FileNotFoundException("helm release not found: " + name);
            case 1:
                return helmSecretRead(lst.get(0).getMetadata().getName());
            default:
                throw new IllegalStateException(lst.toString());
        }
    }

    public List<String> helmList() throws IOException {
        SecretList lst;
        List<String> result;

        result = new ArrayList<>();
        try {
            lst = client.secrets().inNamespace(namespace).withLabels(Strings.toMap("owner", "helm", "status", "deployed")).list();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
        for (Secret secret : lst.getItems()) {
            result.add(secret.getMetadata().getLabels().get("name"));
        }
        return result;
    }

    private JsonObject helmSecretRead(String secretName) throws IOException {
        Secret s;
        byte[] release;

        s = secretRead(secretName);
        release = Base64.getDecoder().decode(s.getData().get("release"));
        release = Base64.getDecoder().decode(release);
        try (Reader src = new InputStreamReader(new GZIPInputStream(new ByteArrayInputStream(release)))) {
            return JsonParser.parseReader(src).getAsJsonObject();
        }
    }

    private Secret secretRead(String name) throws IOException {
        Secret secret;

        try {
            secret = client.secrets().inNamespace(namespace).withName(name).get();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
        if (secret == null) {
            throw new FileNotFoundException("secret/" + name);
        }
        return secret;
    }

    //--

    private static boolean same(String left, String right) {
        if (left == null) {
            return right == null;
        } else {
            return left.equals(right);
        }
    }

    private static IOException wrap(KubernetesClientException e) {
        IOException result;

        if (e.getCode() == 404) {
            result = new FileNotFoundException(e.getMessage());
            result.initCause(e);
            return result;
        }
        return new IOException(e.getMessage(), e);
    }

    //-- json utils

    public static JsonObject obj(Map<String, String> obj) {
        JsonObject result;

        result = new JsonObject();
        for (Map.Entry<String, String> entry : obj.entrySet()) {
            result.add(entry.getKey(), new JsonPrimitive(entry.getValue()));
        }
        return result;
    }

    //--

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

    public Stats statsOpt(String pod, String container) {
        PodMetrics p;
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
        usage = container(p, container).getUsage();
        return new Stats(usage.get("cpu").toString(), usage.get("memory").toString());
    }

    private ContainerMetrics container(PodMetrics p, String container) {
        for (ContainerMetrics cm : p.getContainers()) {
            if (cm.getName().equals(container)) {
                return cm;
            }
        }
        throw new IllegalStateException(p.getMetadata().getName() + " container not found: " + container);
    }

    //--

    public void createServiceAccount(String name) {
        ServiceAccountBuilder sa;

        sa = new ServiceAccountBuilder().withNewMetadata().withNamespace(namespace).withName(name).endMetadata();
        client.serviceAccounts().create(sa.build());
    }

    public List<String> getServiceAccountSecrets(String name) {
        ServiceAccount sa;
        List<String> result;

        result = new ArrayList<>();
        sa = client.serviceAccounts().inNamespace(namespace).withName(name).get();
        for (ObjectReference ref : sa.getSecrets()) {
            result.add(ref.getName());
        }
        return result;
    }

    /** @return base64 encoded token */
    public String getServiceAccountToken(String name) {
        String tokenSecret;
        Secret token;

        tokenSecret = tokenSecret(getServiceAccountSecrets(name));
        token = client.secrets().inNamespace(namespace).withName(tokenSecret).get();
        return token.getData().get("token");
    }

    private String tokenSecret(List<String> secrets) {
        String result;

        result = null;
        for (String secret : secrets) {
            if (secret.contains("-token-")) {
                if (result != null) {
                    throw new IllegalStateException("token secret ambiguous: " + result + " vs " + secret);
                }
                result = secret;
            }
        }
        if (result == null) {
            throw new IllegalStateException("no token secret: " + secrets);
        }
        return result;
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
        ruleBuilder.withApiGroups("")
                .withAttributeRestrictions(null)
                .withResources("pods", "pods/portforward", "pods/exec")
                .withResourceNames(pods).withVerbs("get", "create");
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

    //--

    public LocalPortForward portForward(String pod, int localPort, int podPort) {
        return client.pods().inNamespace(namespace).withName(pod).portForward(podPort, localPort);
    }

    public ExecWatch ssh(String pod, String container, String[] command, ExecListener listener) {
        return client.pods().inNamespace(namespace).withName(pod).inContainer(container)
                .readingInput(System.in)
                .writingOutput(System.out)
                .writingError(System.err)
                .withTTY()
                .usingListener(listener)
                .exec(command);
    }
}
