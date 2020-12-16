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
import com.google.gson.JsonSyntaxException;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.openapi.models.ExtensionsV1beta1HTTPIngressPathBuilder;
import io.kubernetes.client.openapi.models.ExtensionsV1beta1HTTPIngressRuleValueBuilder;
import io.kubernetes.client.openapi.models.ExtensionsV1beta1IngressBuilder;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerBuilder;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentBuilder;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.util.Strings;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Connect to local docker engine via unix socket. https://docs.docker.com/engine/api/v1.37/
 * Not thread-safe because the io buffer is shared.
 */
public class Engine implements AutoCloseable {
    static {
        // default api client doesnt work for multiple thread - i don't want to rely on that, so I set it to null
        // to find places that use it
        Configuration.setDefaultApiClient(null);

    }

    //--

    public static Engine createFromCluster() throws IOException {
        String namespace;
        Engine engine;

        // see https://kubernetes.io/docs/tasks/access-application-cluster/access-cluster/
        namespace = new String(Files.readAllBytes(Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/namespace")),
                StandardCharsets.UTF_8.name());
        // default client automatically detects inCluster config
        engine = new Engine(Config.fromCluster(), namespace);
        return engine;
    }

    public static Engine create(World world, String context) throws IOException {
        KubeConfig config;
        Engine engine;

        try (Reader src = world.getHome().join(KubeConfig.KUBEDIR).join(KubeConfig.KUBECONFIG).newReader()) {
            config = KubeConfig.loadKubeConfig(src);
            if (!config.setContext(context)) {
                throw new IllegalArgumentException(context);
            }
        }
        // default client automatically detects inCluster config
        engine = new Engine(Config.fromConfig(config), config.getNamespace());
        return engine;
    }

    private final ApiClient client;
    private final CoreV1Api core;
    private final AppsV1Api apps;
    private final ExtensionsV1beta1Api extensions;
    private final String namespace;

    private Engine(ApiClient client, String namespace) {
        this.client = client;
        // client.setDebugging(true);
        this.core = new CoreV1Api(client);
        this.apps = new AppsV1Api(client);
        this.extensions = new ExtensionsV1beta1Api(client);
        this.namespace = namespace;
    }

    public String getNamespace() {
        return namespace;
    }

    public void close() {
        // TODO: https://github.com/kubernetes-client/java/issues/865
        client.getHttpClient().connectionPool().evictAll();
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

    //-- ingress

    public void ingressCreate(String name, String host, String serviceName, int servicePort) throws IOException {
        ExtensionsV1beta1IngressBuilder ingress;
        ExtensionsV1beta1HTTPIngressRuleValueBuilder rule;
        ExtensionsV1beta1HTTPIngressPathBuilder path;

        path = new ExtensionsV1beta1HTTPIngressPathBuilder();
        path = path.withPath("/").withNewBackend().withServiceName(serviceName).withServicePort(new IntOrString(servicePort)).endBackend();
        rule = new ExtensionsV1beta1HTTPIngressRuleValueBuilder();
        rule = rule.withPaths(path.build());
        ingress = new ExtensionsV1beta1IngressBuilder()
                .withNewMetadata().withName(name).endMetadata()
                .withNewSpec()
                   .addNewRule().withHost(host).withHttp(rule.build()).endRule().endSpec();
        try {
            extensions.createNamespacedIngress(namespace, ingress.build(), null, null, null);
        } catch (ApiException e) {
            throw wrap(e);
        }
    }

    public void ingressDelete(String name) throws IOException {
        try {
            extensions.deleteNamespacedIngress(name, namespace, null, null, null,
                    null, null, null);
        } catch (ApiException e) {
            throw wrap(e);
        }
    }

    //-- deployments

    public Map<String, DeploymentInfo> deploymentList() throws IOException {
        V1DeploymentList list;
        Map<String, DeploymentInfo> result;
        String name;

        try {
            list = apps.listNamespacedDeployment(namespace, null, null, null, null, null,
                    null, null, null, null);
            result = new LinkedHashMap<>();
            for (V1Deployment deployment : list.getItems()) {
                name = deployment.getMetadata().getName();
                result.put(name, DeploymentInfo.create(deployment));
            }
        } catch (ApiException e) {
            throw wrap(e);
        }
        return result;
    }

    public DeploymentInfo deploymentProbe(String name) throws IOException {
        try {
            return DeploymentInfo.create(apps.readNamespacedDeployment(name, namespace, null, null, null));
        } catch (ApiException e) {
            try {
                throw wrap(e);
            } catch (FileNotFoundException ignored) {
                return null;
            }
        }
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
        deploymentCreate(name, selector, deploymentLabels, new Container[] { new Container(image, command) }, hostname, podLabels);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public void deploymentCreate(String name, Map<String, String> selector, Map<String, String> deploymentLabels,
                                    Container[] containers, String hostname, Map<String, String> podLabels) throws IOException {
        try {
            apps.createNamespacedDeployment(namespace, deployment(name, selector, deploymentLabels, containers, hostname, podLabels),
                    null, null, null);
        } catch (ApiException e) {
            throw wrap(e);
        }
    }

    /** @return containerId or null */
    public void deploymentDelete(String name) throws IOException {
        try {
            apps.deleteNamespacedDeployment(name, namespace, null,
                    null, null, null, null,  null);
        } catch (JsonSyntaxException e) {
            if (e.getMessage().contains("java.lang.IllegalStateException: Expected a string but was BEGIN_OBJECT")) {
                // TODO The Java Client is generated, and this code generation does not support union return types,
                //      see https://github.com/kubernetes-client/java/issues/86
                // TODO: check if pod was actually deletes
                // fall-through
            } else {
                throw e;
            }
        } catch (ApiException e) {
            throw wrap(e);
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private V1Deployment deployment(String name, Map<String, String> selector, Map<String, String> deploymentLabels,
                           Container[] containers, String hostname, Map<String, String> podLabels) {
        List<V1Container> cl;

        cl = new ArrayList<>();
        for (Container c : containers) {
            cl.add(c.build());
        }
        return new V1DeploymentBuilder()
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
        return doPodList(null);
    }

    public Map<String, PodInfo> podList(Map<String, String> labelSelector) throws IOException {
        return doPodList(labelSelector(labelSelector));
    }

    private Map<String, PodInfo> doPodList(String labelSelector) throws IOException {
        V1PodList list;
        Map<String, PodInfo> result;
        String name;

        try {
            list = core.listNamespacedPod(namespace, null, null, null, null, labelSelector,
                    null, null, null, null);
            result = new LinkedHashMap<>();
            for (V1Pod pod : list.getItems()) {
                name = pod.getMetadata().getName();
                result.put(name, PodInfo.create(pod));
            }
        } catch (ApiException e) {
            throw wrap(e);
        }
        return result;
    }

    /** @return null if not found */
    public PodInfo podProbe(String name) throws IOException {
        try {
            return PodInfo.create(core.readNamespacedPod(name, namespace, null, null, null));
        } catch (ApiException e) {
            try {
                throw wrap(e);
            } catch (FileNotFoundException ignored) {
                return null;
            }
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public boolean podCreate(String name, String image, String[] command, String hostname, boolean healing, Map<String, String> labels) throws IOException {
        String phase;

        try {
            core.createNamespacedPod(namespace, pod(name, new Container(image, command), hostname, healing, labels), null, null, null);
        } catch (ApiException e) {
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
            core.deleteNamespacedPod(name, namespace, null,
                    null, null, null, null,  null);
        } catch (JsonSyntaxException e) {
            if (e.getMessage().contains("java.lang.IllegalStateException: Expected a string but was BEGIN_OBJECT")) {
                // TODO The Java Client is generated, and this code generation does not support union return types,
                //      see https://github.com/kubernetes-client/java/issues/86
                // TODO: check if pod was actually deletes
                // fall-through
            } else {
                throw e;
            }
        } catch (ApiException e) {
            throw wrap(e);
        }
        podAwait(name, null);
    }

    public boolean podContainerRunning(String podName, String containerName) throws IOException {
        V1ContainerStatus status;
        V1ContainerState state;

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

    private V1ContainerStatus getPodContainerStatus(String podName, String containerName) throws IOException {
        V1Pod pod;
        List<V1ContainerStatus> lst;

        try {
            pod = core.readNamespacedPod(podName, namespace, null, null, null);
        } catch (ApiException e) {
            throw wrap(e);
        }
        lst = pod.getStatus().getContainerStatuses();
        for (V1ContainerStatus status : lst) {
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
            return core.readNamespacedPodLog(pod, namespace, null, false, null, null, null, null, null, null);
        } catch (ApiException e) {
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

    public static class Container {
        public final String image;
        public final String[] command;

        public Container(String image, String... command) {
            this.image = image;
            this.command = command;
        }

        public V1Container build() {
            Map<String, Quantity> limits;
            V1ContainerBuilder container;
            List<V1EnvVar> lst;

            limits = new HashMap<>();
            lst = new ArrayList<>();
            container = new V1ContainerBuilder();
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
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private V1Pod pod(String name, Container container, String hostname, boolean healing, Map<String, String> labels) {
        return new V1PodBuilder()
                .withNewMetadata().withName(name).withLabels(labels).endMetadata()
                .withNewSpec()
                .withRestartPolicy(healing ? "Always" : "Never")
                .withHostname(hostname)
                .addAllToVolumes(new ArrayList<>())
                .addToContainers(container.build())
                .endSpec().build();
    }

    //-- helm

    public JsonObject helmRead(String name) throws IOException {
        List<V1Secret> lst;

        try {
            lst = core.listNamespacedSecret(namespace, null, null, null, null,
                    labelSelector(Strings.toMap("owner", "helm", "name", name, "status", "deployed")),
                    null, null, null, null).getItems();
        } catch (ApiException e) {
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
        V1SecretList lst;
        List<String> result;

        result = new ArrayList<>();
        try {
            lst = core.listNamespacedSecret(namespace, null, null, null, null,
                    labelSelector(Strings.toMap("owner", "helm", "status", "deployed")),
                    null, null, null, null);
        } catch (ApiException e) {
            throw wrap(e);
        }
        for (V1Secret secret : lst.getItems()) {
            result.add(secret.getMetadata().getLabels().get("name"));
        }
        return result;
    }

    private JsonObject helmSecretRead(String secretName) throws IOException {
        V1Secret s;
        byte[] release;

        s = secretRead(secretName);
        release = s.getData().get("release");
        release = Base64.getDecoder().decode(release);
        try (Reader src = new InputStreamReader(new GZIPInputStream(new ByteArrayInputStream(release)))) {
            return JsonParser.parseReader(src).getAsJsonObject();
        }
    }
    private V1Secret secretRead(String name) throws IOException {
        try {
            return core.readNamespacedSecret(name, namespace, null, null, null);
        } catch (ApiException e) {
            throw wrap(e);
        }
    }

    //--

    private static boolean same(String left, String right) {
        if (left == null) {
            return right == null;
        } else {
            return left.equals(right);
        }
    }

    private static IOException wrap(ApiException e) {
        IOException result;

        if (e.getCode() == 404) {
            result = new java.io.FileNotFoundException(e.getResponseBody());
            result.initCause(e);
            return result;
        }
        return new IOException(e.getResponseBody(), e);
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
}
