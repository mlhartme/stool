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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapBuilder;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStateRunning;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
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
import net.oneandone.stool.docker.Daemon;
import net.oneandone.stool.docker.ImageInfo;
import net.oneandone.stool.docker.Registry;
import net.oneandone.stool.server.ArgumentException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.HttpNode;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.time.format.DateTimeFormatter;
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
    private static final String UTF_8 = "utf8";

    public static String encodeLabel(String value) {
        try {
            return "a-" + Base64.getEncoder().encodeToString(value.getBytes(UTF_8)).replace('=', '-') + "-z";
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String decodeLabel(String value) {
        value = Strings.removeLeft(value, "a-");
        value = Strings.removeRight(value, "-z");
        value = value.replace('-', '=');
        try {
            return new String(Base64.getDecoder().decode(value), UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    //--

    public static Engine create() throws IOException {
        Engine result;
        PodInfo r;
        HttpNode root;
        JsonObject i;

        result = new Engine(null);
        r = result.podProbe("stool-registry");
        // TODO
        if (r != null) {
            System.out.println("registry " + r.ip);
            root = (HttpNode) World.create().validNode("http://" + r.ip + ":5000");
            result.registry = Registry.create(root, null);
        }
        return result;
    }

    private final ApiClient client;
    private final CoreV1Api core;
    private final String namespace;
    public Registry registry;

    private Engine(Registry registry) throws IOException {
        this.registry = registry;

        client = Config.defaultClient();
        Configuration.setDefaultApiClient(client); // TODO: threading ...

        // client.setDebugging(true);
        core = new CoreV1Api();
        namespace = "stool";
    }

    public void close() {
        // TODO: https://github.com/kubernetes-client/java/issues/865
        client.getHttpClient().connectionPool().evictAll();
    }


    //--

    public String version() throws IOException {
        return "TODO";
    }

    //-- images

    /** @return image ids mapped to ImageInfo */
    public Map<String, net.oneandone.stool.docker.ImageInfo> imageList() throws IOException {
        return imageList(Collections.emptyMap());
    }

    // TODO: performance, caching
    public Map<String, ImageInfo> imageList(Map<String, String> labels) throws IOException {
        ImageInfo info;
        Map<String, ImageInfo> result;

        result = new HashMap<>();
        for (String repository : registry.catalog()) {
            for (String tag : registry.tags(repository)) {
                info = registry.info(repository, tag);
                if (info.matches(labels)) {
                    result.put(info.id, info);
                }
            }
        }
        return result;
    }

    //-- namespace

    public void namespaceReset() throws IOException {
        if (namespaceList().containsKey(namespace)) {
            namespaceDelete();
        }
        namespaceCreate();
    }

    public void namespaceCreate() throws IOException {
        try {
            core.createNamespace(new V1NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build(),
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

    public void serviceCreate(String name, int nodePort, int containerPort, String... selector) throws IOException {
        serviceCreate(name, nodePort, containerPort, Strings.toMap(selector));
    }

    public void serviceCreate(String name, int nodePort, int containerPort, Map<String, String> selector) throws IOException {
        serviceCreate(name, nodePort, containerPort, selector, Strings.toMap());
    }

    public void serviceCreate(String name, int nodePort, int containerPort, Map<String, String> selector, Map<String, String> labels)
            throws IOException {
        V1ServicePort port;

        port = new V1ServicePort();
        port.setNodePort(nodePort);
        port.setPort(containerPort);
        try {
            core.createNamespacedService(namespace, new V1ServiceBuilder()
                    .withNewMetadata().withName(name).withLabels(labels).endMetadata()
                    .withNewSpec().withType("NodePort").withPorts(port).withSelector(selector).endSpec()
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

    //-- pods

    public Map<String, PodInfo> podList() throws IOException {
        V1PodList list;
        Map<String, PodInfo> result;
        String name;

        try {
            list = core.listNamespacedPod(namespace, null, null, null, null, null,
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

    // TODO
    public PodInfo podProbe(String name) throws IOException {
        V1PodList list;

        try {
            list = core.listNamespacedPod(namespace, null, null, null, null, null,
                    null, null, null, null);
            for (V1Pod pod : list.getItems()) {
                if (name.equals(pod.getMetadata().getName())) {
                    return PodInfo.create(pod);
                }
            }
        } catch (ApiException e) {
            throw wrap(e);
        }
        return null;
    }

    public boolean podCreate(String name, String image, String... labels) throws IOException {
        return podCreate(name, image, Strings.toMap(labels));
    }

    public boolean podCreate(String name, String image, Map<String, String> labels) throws IOException {
        return podCreate(name, image, labels, Strings.toMap());
    }

    public boolean podCreate(String name, String image, Map<String, String> labels, Map<String, String> env) throws IOException {
        return podCreate(name, image, null, false, null, labels, env, Collections.emptyMap(), Collections.emptyList());
    }

    public boolean podCreate(String name, String image, String hostname, boolean healing, Integer memory, Map<String, String> labels, Map<String, String> env,
                          Map<FileNode, String> hostVolumes, List<Data> dataVolumes) throws IOException {
        String phase;

        try {
            core.createNamespacedPod(namespace, pod(name, image, hostname, healing, memory, labels, env, hostVolumes, dataVolumes), null, null, null);
        } catch (ApiException e) {
            throw wrap(e);
        }

        phase = podAwait(name, "Running", "Failed", "Succeeded");
        if (phase.equals("Failed")) {
            throw new IOException("create-pod failed: " + phase);
        }
        return "Running".equals(phase);
    }

    /** @return containerId or null */
    public String podDelete(String name) throws IOException {
        PodInfo info;

        info = podProbe(name);
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
        return info == null ? null : info.containerId;
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

    private String podAwait(String name, String... expectedPhases) throws IOException {
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

    public void podLogsFollow(String pod, OutputStream dest) throws IOException {
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

    /** @param dataVolumes  ([Boolean secrets, String secret name, String dest path], (key, path)*)* */
    private static V1Pod pod(String name, String image, String hostname, boolean healing, Integer memory,
                             Map<String, String> labels, Map<String, String> env, Map<FileNode, String> hostVolumes,
                             List<Data> dataVolumes) {
        List<V1EnvVar> lst;
        V1EnvVar var;
        List<V1Volume> vl;
        V1Volume v;
        int volumeCount;
        String vname;
        V1HostPathVolumeSource hp;
        List<V1VolumeMount> ml;
        V1VolumeMount m;
        Map<String, Quantity> limits;

        lst = new ArrayList<>();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            var = new V1EnvVar();
            var.setName(entry.getKey());
            var.setValue(entry.getValue());
            lst.add(var);
        }
        vl = new ArrayList<>();
        ml = new ArrayList<>();
        volumeCount = 0;
        for (Map.Entry<FileNode, String> entry : hostVolumes.entrySet()) {
            hp = new V1HostPathVolumeSource();
            hp.setPath(entry.getKey().getAbsolute());
            vname = "volume" + ++volumeCount;
            v = new V1Volume();
            v.setName(vname);
            v.setHostPath(hp);
            vl.add(v);
            m = new V1VolumeMount();
            m.setName(vname);
            m.setMountPath(entry.getValue());
            ml.add(m);
        }
        for (Data data : dataVolumes) {
            vname = "volume" + ++volumeCount;
            vl.add(data.volume(vname));
            data.mounts(vname, ml);
        }
        limits = new HashMap<>();
        if (memory != null) {
            limits.put("memory", new Quantity(memory.toString()));
        }
        return new V1PodBuilder()
                .withNewMetadata().withName(name).withLabels(labels).endMetadata()
                .withNewSpec()
                .withRestartPolicy(healing ? "Always" : "Never")
                .withHostname(hostname)
                .addAllToVolumes(vl)
                .addNewContainer()
                  .addAllToVolumeMounts(ml)
                  .withNewResources().withLimits(limits).endResources()
                  .withName(name + "-container")
                  .withImage(image)
                  .withEnv(lst)
                  .withImagePullPolicy("Never") // TODO
                .endContainer().endSpec().build();
    }

    //-- secrets

    public void secretCreate(String name, Map<String, byte[]> data) throws IOException {
        V1Secret secret;

        secret = new V1SecretBuilder().withNewMetadata().withName(name).withNamespace(namespace).endMetadata().withData(data).build();
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
    public Map<String, String> secretList() throws IOException {
        V1SecretList lst;
        Map<String, String> result;

        try {
            lst = core.listNamespacedSecret(namespace, null, null, null, null, null, null, null, null, null);
            result = new HashMap();
            for (V1Secret ns : lst.getItems()) {
                result.put(ns.getMetadata().getName(), ns.getMetadata().getName());
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

    /** @return name- to phase mapping */
    public Map<String, String> configMapList() throws IOException {
        V1ConfigMapList lst;
        Map<String, String> result;

        try {
            lst = core.listNamespacedConfigMap(namespace, null, null, null, null, null, null, null, null, null);
            result = new HashMap();
            for (V1ConfigMap m : lst.getItems()) {
                result.put(m.getMetadata().getName(), m.getMetadata().getName()); // TODO: more info
            }
        } catch (ApiException e) {
            throw wrap(e);
        }
        return result;
    }

    public void configMapCreate(String name, Map<String, byte[]> data) throws IOException {
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

    // this is to avoid engine 500 error reporting "invalid reference format: repository name must be lowercase"
    public static void validateReference(String reference) {
        char c;

        for (int i = 0, length = reference.length(); i < length; i++) {
            if (Character.isUpperCase(reference.charAt(i))) {
                throw new ArgumentException("invalid reference: " + reference);
            }
        }
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

    public static Map<String, String> toStringMap(JsonObject obj) {
        Map<String, String> result;

        result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }
}
