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
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerBuilder;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStateRunning;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentBuilder;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceBuilder;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import net.oneandone.stool.docker.Daemon;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.util.Strings;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static final String UTF_8 = "utf8";

    public static String encodeLabel(String str) {
        String result;

        result = encodeLabelRaw(str);
        if (result.length() > 63) {
            throw new IllegalStateException("value too long: " + str);
        }
        return result;
    }

    public static String encodeLabelRaw(String str) {
        try {
            return "a-" + Base64.getEncoder().encodeToString(str.getBytes(UTF_8)).replace('=', '-') + "-z";
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String decodeLabel(String str) {
        str = Strings.removeLeft(str, "a-");
        str = Strings.removeRight(str, "-z");
        str = str.replace('-', '=');
        try {
            return new String(Base64.getDecoder().decode(str), UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    //--

    public static Engine createFromCluster(Map<String, String> implicitLabels) throws IOException {
        String namespace;
        Engine engine;

        // see https://kubernetes.io/docs/tasks/access-application-cluster/access-cluster/
        namespace = new String(Files.readAllBytes(Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/namespace")),
                StandardCharsets.UTF_8.name());
        // default client automatically detects inCluster config
        engine = new Engine(Config.fromCluster(), namespace);
        engine.implicitLabels.putAll(implicitLabels);
        return engine;
    }

    public static Engine create(World world, String context, Map<String, String> implicitLabels) throws IOException {
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
        engine.implicitLabels.putAll(implicitLabels);
        return engine;
    }

    private final ApiClient client;
    private final CoreV1Api core;
    private final AppsV1Api apps;
    private final ExtensionsV1beta1Api extensions;
    private final String namespace;
    private final Map<String, String> implicitLabels;

    private Engine(ApiClient client, String namespace) {
        this.client = client;
        // client.setDebugging(true);
        this.core = new CoreV1Api(client);
        this.apps = new AppsV1Api(client);
        this.extensions = new ExtensionsV1beta1Api(client);
        this.namespace = namespace;
        this.implicitLabels = new HashMap<>();
    }

    public void addImplicitLabel(String key, String value) {
        implicitLabels.put(key, value);
    }

    public String getNamespace() {
        return namespace;
    }

    public void close() {
        // TODO: https://github.com/kubernetes-client/java/issues/865
        client.getHttpClient().connectionPool().evictAll();
    }


    //--

    public String version() throws IOException {
        return "TODO";
    }

    //-- namespace

    public void namespaceReset() throws IOException {
        for (DeploymentInfo deployment : deploymentList().values()) {
            if (hasImplicit(deployment.labels)) {
                System.out.println("delete deployment: " + deployment.name);
                deploymentDelete(deployment.name);
            }
        }
        for (PodInfo pod: podList().values()) {
            if (hasImplicit(pod.labels)) {
                System.out.println("delete pod: " + pod.name);
                podDelete(pod.name);
            }
        }
        for (ServiceInfo service : serviceList().values()) {
            if (hasImplicit(service.labels)) {
                System.out.println("delete service: " + service.name);
                serviceDelete(service.name);
            }
        }
        for (DataInfo cm : configMapList().values()) {
            if (hasImplicit(cm.labels)) {
                System.out.println("delete configMap: " + cm.name);
                configMapDelete(cm.name);
            }
        }
        for (DataInfo s : secretList().values()) {
            if (hasImplicit(s.labels)) {
                System.out.println("delete secret: " + s.name);
                secretDelete(s.name);
            }
        }
    }

    public void namespaceCreate() throws IOException {
        try {
            core.createNamespace(new V1NamespaceBuilder()
                            .withNewMetadata().withLabels(implicitLabels).withName(namespace).endMetadata()
                            .build(),
                    null, null, null);
        } catch (ApiException e) {
            throw wrap(e);
        }

        namespaceAwait("Active");
        try { // TODO: avoids "No API token found for service account" in follow-up calls
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    public void namespaceDelete() throws IOException {
        try {
            try {
                core.deleteNamespace(namespace, null, null, null, null, "Foreground", null);
            } catch (JsonSyntaxException e) {
                if (e.getMessage().contains("java.lang.IllegalStateException: Expected a string but was BEGIN_OBJECT")) {
                    // TODO The Java Client is generated, and this code generation does not support union return types,
                    //      see https://github.com/kubernetes-client/java/issues/86
                    // fall-through
                } else {
                    throw e;
                }
            }
            namespaceAwait(null);
        } catch (ApiException e) {
            throw wrap(e);
        }
    }

    /** @return name- to phase mapping */
    public Map<String, String> namespaceList() throws IOException {
        V1NamespaceList lst;
        Map<String, String> result;

        try {
            lst = core.listNamespace(null, null, null, null, null, null, null, null, null);
            result = new HashMap();
            for (V1Namespace ns : lst.getItems()) {
                result.put(ns.getMetadata().getName(), ns.getStatus().getPhase());
            }
        } catch (ApiException e) {
            throw wrap(e);
        }
        return result;
    }

    private void namespaceAwait(String expectedPhase) throws IOException {
        int count;
        String phase;

        count = 0;
        while (true) {
            phase = namespaceList().get(namespace);
            if (same(expectedPhase, phase)) {
                return;
            }
            count++;
            if (count > 500) {
                throw new IOException("waiting for namespace phase '" + expectedPhase + "' timed out, phase now is " + phase);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new IOException("waiting for namespace phase '" + expectedPhase + "' interrupted");
            }
        }

    }

    //-- services

    public ServiceInfo serviceGetOpt(String name) throws IOException {
        try {
            return serviceGet(name);
        } catch (java.io.FileNotFoundException e) {
            return null;
        }
    }

    public ServiceInfo serviceGet(String name) throws IOException {
        V1Service service;

        try {
            service = core.readNamespacedService(name, namespace, null, null, null);
        } catch (ApiException e) {
            throw wrap(e);
        }
        return ServiceInfo.create(service);
    }

    public void serviceCreate(String name, int port, int targetPort, String... selector) throws IOException {
        serviceCreate(name, port, targetPort, Strings.toMap(selector));
    }

    public void serviceCreate(String name, int port, int targetPort, Map<String, String> selector) throws IOException {
        serviceCreate(name, port, targetPort, selector, Strings.toMap());
    }

    public void serviceCreate(String name, int port, int targetPort, Map<String, String> selector, Map<String, String> labels)
            throws IOException {
        serviceCreate(name, Collections.singletonList("p"), Collections.singletonList(port), Collections.singletonList(targetPort),
                selector, labels);
    }

    public void serviceCreate(String name, List<String> portNames, List<Integer> ports, List<Integer> targetPorts, Map<String, String> selector,
                              Map<String, String> labels)
            throws IOException {
        int count;
        List<V1ServicePort> lst;
        V1ServicePort p;

        count = portNames.size();
        if (count != ports.size() || count != targetPorts.size()) {
            throw new IllegalArgumentException(count + " vs " + ports.size() + " vs " + targetPorts.size());
        }
        lst = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            p = new V1ServicePort();
            p.setName(portNames.get(i));
            p.setPort(ports.get(i));
            p.setTargetPort(new IntOrString(targetPorts.get(i)));
            lst.add(p);
        }
        try {
            core.createNamespacedService(namespace, new V1ServiceBuilder()
                    .withNewMetadata().withName(name).withLabels(withImplicit(labels)).endMetadata()
                    .withNewSpec().withType("ClusterIP").withPorts(lst).withSelector(selector).endSpec()
                    .build(), null, null, null);
        } catch (ApiException e) {
            throw wrap(e);
        }
    }

    public Map<String, ServiceInfo> serviceList() throws IOException {
        V1ServiceList list;
        Map<String, ServiceInfo> result;
        ServiceInfo info;

        try {
            list = core.listNamespacedService(namespace, null, null, null, null, null,
                    null, null, null, null);
            result = new HashMap<>();
            for (V1Service service: list.getItems()) {
                info = ServiceInfo.create(service);
                result.put(info.name, info);
            }
        } catch (ApiException e) {
            throw wrap(e);
        }
        return result;
    }

    public void serviceDelete(String name) throws IOException {
        try {
            core.deleteNamespacedService(name, namespace, null, null, null, null,
                    null, null);
        } catch (ApiException e) {
            throw wrap(e);
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
                .withNewMetadata().withName(name).withLabels(implicitLabels).endMetadata()
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

    private DeploymentInfo deploymentAwait(String name) throws IOException {
        DeploymentInfo info;
        int count;

        count = 0;
        while (true) {
            info = deploymentProbe(name);
            if (info.available > 0) {
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

    public void deploymentCreate(String name, Map<String, String> selector, Map<String, String> deploymentLabels,
                                 Container container, String hostname, Map<String, String> podLabels) throws IOException {
        deploymentCreate(name, selector, deploymentLabels, new Container[] { container }, hostname, podLabels);
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
        deploymentAwait(name);
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
        List<V1Volume> vl;
        List<V1Container> cl;

        vl = new ArrayList<>();
        cl = new ArrayList<>();
        for (Container c : containers) {
            c.volumes(vl);
            cl.add(c.build());
        }
        return new V1DeploymentBuilder()
                .withNewMetadata()
                  .withName(name)
                  .withLabels(withImplicit(deploymentLabels))
                .endMetadata()
                .withNewSpec()
                  .withReplicas(1)
                  .withNewSelector().withMatchLabels(selector).endSelector()
                  .withNewTemplate()
                    .withNewMetadata().withLabels(withImplicit(podLabels)).endMetadata()
                    .withNewSpec()
                      .withHostname(hostname)
                      .addAllToVolumes(vl)
                      .withContainers(cl)
                    .endSpec()
                  .endTemplate()
                .endSpec().build();
    }

    public boolean hasImplicit(Map<String, String> labels) {
        return labels.entrySet().containsAll(implicitLabels.entrySet());
    }

    private Map<String, String> withImplicit(Map<String, String> explicit) {
        Map<String, String> result;

        if (explicit.isEmpty()) {
            return implicitLabels;
        }
        result = new HashMap<>(implicitLabels);
        result.putAll(explicit);
        return result;
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

    public boolean podCreate(String name, Container container, String... labels) throws IOException {
        return podCreate(name, container, Strings.toMap(labels));
    }

    public boolean podCreate(String name, Container container, Map<String, String> labels) throws IOException {
        return podCreate(name, container, null, false, labels);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public boolean podCreate(String name, Container container, String hostname, boolean healing, Map<String, String> labels) throws IOException {
        String phase;

        try {
            core.createNamespacedPod(namespace, pod(name, container, hostname, healing, labels), null, null, null);
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

    public Daemon.Status podContainerStatus(String name) throws IOException {
        V1ContainerStatus status;
        V1ContainerState state;

        status = getPodContainerStatus(name);
        state = status.getState();
        if (state.getTerminated() != null) {
            return Daemon.Status.EXITED;
        }
        if (state.getRunning() != null) {
            return Daemon.Status.RUNNING;
        }
        throw new IOException("unknown state: " + state);
    }

    private V1ContainerStatus getPodContainerStatus(String name) throws IOException {
        V1Pod pod;
        List<V1ContainerStatus> lst;

        try {
            pod = core.readNamespacedPod(name, namespace, null, null, null);
        } catch (ApiException e) {
            throw wrap(e);
        }
        lst = pod.getStatus().getContainerStatuses();
        if (lst.size() != 1) {
            throw new IllegalStateException(lst.toString());
        }
        return lst.get(0);
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

    public void podLogsFollow(String pod, OutputStream dest) {
        throw new IllegalStateException("TODO");
    }


    public Long podStartedAt(String pod) throws IOException {
        V1ContainerStatus status;
        V1ContainerStateRunning running;

        status = getPodContainerStatus(pod);
        running = status.getState().getRunning();
        if (running == null) {
            return null;
        }
        return running.getStartedAt().toDate().getTime();
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
        public final String name;
        public final String image;
        public final String[] command;
        public final boolean imagePull;
        public final Map<String, String> env;
        public final Integer cpu;
        public final Integer memory;
        public final Map<Data.Mount, Data> mounts; // maps volume names to mount paths

        public Container(String image, String... command) {
            this("noname", image, command, false, Collections.emptyMap(), null, null,
                    Collections.emptyMap());
        }

        public Container(String name, String image, String[] command, boolean imagePull, Map<String, String> env, Integer cpu, Integer memory, Map<Data.Mount, Data> mounts) {
            this.name = name;
            this.image = image;
            this.command = command;
            this.imagePull = imagePull;
            this.env = env;
            this.cpu = cpu;
            this.memory = memory;
            this.mounts = mounts;
        }

        public void volumes(List<V1Volume> result) {
            for (Data data : mounts.values()) {
                result.add(data.volume());
            }
        }

        public V1Container build() {
            Map<String, Quantity> limits;
            V1ContainerBuilder container;
            List<V1EnvVar> lst;
            V1EnvVar var;

            limits = new HashMap<>();
            if (cpu != null) {
                limits.put("cpu", new Quantity(cpu.toString()));
            }
            if (memory != null) {
                limits.put("memory", new Quantity(memory.toString()));
            }
            lst = new ArrayList<>();
            for (Map.Entry<String, String> entry : env.entrySet()) {
                var = new V1EnvVar();
                var.setName(entry.getKey());
                var.setValue(entry.getValue());
                lst.add(var);
            }
            container = new V1ContainerBuilder();
            container.addAllToVolumeMounts(mountList())
                    .withNewResources().withLimits(limits).endResources()
                    .withName(name)
                    .withImage(image)
                    .withEnv(lst)
                    .withImagePullPolicy(imagePull ? "IfNotPresent" : "Never");

            if (command != null) {
                container.withCommand(command);
            }
            return container.build();
        }

        private List<V1VolumeMount> mountList() {
            List<V1VolumeMount> result;

            result = new ArrayList<>();
            for (Map.Entry<Data.Mount, Data> entry : mounts.entrySet()) {
                entry.getValue().mounts(entry.getKey(), result);
            }
            return result;
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private V1Pod pod(String name, Container container, String hostname, boolean healing, Map<String, String> labels) {
        List<V1Volume> vl;

        vl = new ArrayList<>();
        container.volumes(vl);
        return new V1PodBuilder()
                .withNewMetadata().withName(name).withLabels(withImplicit(labels)).endMetadata()
                .withNewSpec()
                .withRestartPolicy(healing ? "Always" : "Never")
                .withHostname(hostname)
                .addAllToVolumes(vl)
                .addToContainers(container.build())
                .endSpec().build();
    }

    //-- secrets

    public void secretCreate(String name, Map<String, byte[]> data) throws IOException {
        V1Secret secret;

        secret = new V1SecretBuilder().withNewMetadata().withName(name).withLabels(implicitLabels).withNamespace(namespace).endMetadata()
                .withData(data).build();
        try {
            core.createNamespacedSecret(namespace, secret, null, null, null);
        } catch (ApiException e) {
            throw wrap(e);
        }
    }

    public void secretDelete(String name) throws IOException {
        try {
            core.deleteNamespacedSecret(name, namespace, null, null, null, null, "Foreground", null);
        } catch (ApiException e) {
            throw wrap(e);
        }
        awaitSecretDeleted(name);
    }

    /** @return name- to phase mapping */
    public Map<String, DataInfo> secretList() throws IOException {
        V1SecretList lst;
        Map<String, DataInfo> result;

        try {
            lst = core.listNamespacedSecret(namespace, null, null, null, null, null, null, null, null, null);
            result = new HashMap();
            for (V1Secret ns : lst.getItems()) {
                result.put(ns.getMetadata().getName(), DataInfo.create(ns.getMetadata()));
            }
        } catch (ApiException e) {
            throw wrap(e);
        }
        return result;
    }

    private void awaitSecretDeleted(String name) throws IOException {
        int count;

        count = 0;
        while (true) {
            try {
                try {
                    core.readNamespacedSecret(name, namespace, null, null, null);
                } catch (ApiException e) {
                    throw wrap(e);
                }
            } catch (java.io.FileNotFoundException e) {
                return;
            }
            count++;
            if (count > 500) {
                throw new IOException("waiting for delete timed out");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new IOException("waiting for delete timed interrupted");
            }
        }

    }

    //-- config maps

    public Map<String, DataInfo> configMapList() throws IOException {
        return doConfigMapList(null);
    }

    public Map<String, DataInfo> configMapList(Map<String, String> labelSelector) throws IOException {
        return doConfigMapList(labelSelector(labelSelector));
    }

    public Map<String, DataInfo> doConfigMapList(String labelSelector) throws IOException {
        V1ConfigMapList lst;
        Map<String, DataInfo> result;

        try {
            lst = core.listNamespacedConfigMap(namespace, null, null, null, null, labelSelector,
                    null, null, null, null);
            result = new HashMap();
            for (V1ConfigMap m : lst.getItems()) {
                result.put(m.getMetadata().getName(), DataInfo.create(m.getMetadata()));
            }
        } catch (ApiException e) {
            throw wrap(e);
        }
        return result;
    }

    public void configMapCreate(String name, Map<String, String> data) throws IOException {
        configMapCreate(name, data, Collections.emptyMap());
    }

    public void configMapCreate(String name, Map<String, String> data, Map<String, String> labels) throws IOException {
        V1ConfigMap map;

        map = new V1ConfigMapBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).withLabels(withImplicit(labels)).endMetadata()
                .withData(data).build();
        try {
            core.createNamespacedConfigMap(namespace, map, null, null, null);
        } catch (ApiException e) {
            throw wrap(e);
        }
    }

    public Map<String, String> configMapRead(String name) throws IOException {
        V1ConfigMap map;

        try {
            map = core.readNamespacedConfigMap(name, namespace, null, null, null);
            return map.getData();
        } catch (ApiException e) {
            throw wrap(e);
        }
    }

    public void configMapCreateBinary(String name, Map<String, byte[]> data) throws IOException {
        V1ConfigMap map;

        map = new V1ConfigMapBuilder().withNewMetadata().withName(name).withNamespace(namespace).endMetadata().withBinaryData(data).build();
        try {
            core.createNamespacedConfigMap(namespace, map, null, null, null);
        } catch (ApiException e) {
            throw wrap(e);
        }
    }

    public void configMapDelete(String name) throws IOException {
        try {
            core.deleteNamespacedConfigMap(name, namespace, null, null, null, null, "Foreground", null);
        } catch (ApiException e) {
            throw wrap(e);
        }
        awaitConfigMapDeleted(name);
    }

    private void awaitConfigMapDeleted(String name) throws IOException {
        int count;

        count = 0;
        while (true) {
            try {
                try {
                    core.readNamespacedConfigMap(name, namespace, null, null, null);
                } catch (ApiException e) {
                    throw wrap(e);
                }
            } catch (java.io.FileNotFoundException e) {
                return;
            }
            count++;
            if (count > 500) {
                throw new IOException("waiting for delete timed out");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new IOException("waiting for delete timed interrupted");
            }
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
