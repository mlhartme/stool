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
package net.oneandone.stool.server.kubernetes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceBuilder;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.util.Config;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;
import net.oneandone.stool.server.ArgumentException;
import net.oneandone.stool.server.util.FileNodes;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.HttpFilesystem;
import net.oneandone.sushi.fs.http.HttpNode;
import net.oneandone.sushi.fs.http.StatusException;
import net.oneandone.sushi.fs.http.io.AsciiInputStream;
import net.oneandone.sushi.fs.http.model.Body;
import net.oneandone.sushi.fs.http.model.Method;
import net.oneandone.sushi.util.Strings;

import javax.net.SocketFactory;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Connect to local docker engine via unix socket. https://docs.docker.com/engine/api/v1.37/
 * Not thread-safe because the io buffer is shared.
 */
public class Engine implements AutoCloseable {
    //--

    public static final DateTimeFormatter CREATED_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.n'Z'");

    public enum Status {
        CREATED,
        RUNNING,
        EXITED,
        REMOVING /* not used in my code, by docker engine documentation says it can be returned */
    }

    public static Engine create() throws IOException {
        return create(null);
    }

    public static Engine create(String wirelog) throws IOException {
        return create("/var/run/docker.sock", wirelog);
    }

    public static Engine create(String socketPath, String wirelog) throws IOException {
        World world;
        HttpFilesystem fs;
        HttpNode root;

        // CAUTION: local World because I need a special socket factory and multiple Engine instances must *not* share the same buffers
        world = World.create();
        if (wirelog != null) {
            HttpFilesystem.wireLog(wirelog);
        }
        fs = (HttpFilesystem) world.getFilesystem("http");
        fs.setSocketFactorySelector((String protocol, String hostname) ->
                new SocketFactory() {
                    @Override
                    public Socket createSocket(String s, int i) throws IOException {
                        return socket();
                    }

                    @Override
                    public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) throws IOException {
                        return socket();
                    }

                    @Override
                    public Socket createSocket(InetAddress inetAddress, int i) throws IOException {
                        return socket();
                    }

                    @Override
                    public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) throws IOException {
                        return socket();
                    }

                    private Socket socket() throws IOException {
                        UnixSocketAddress address;

                        address = new UnixSocketAddress(new File(socketPath));
                        return UnixSocketChannel.open(address).socket();
                    }
                }
        );
        root = (HttpNode) world.validNode("http://localhost/v1.38");
        root.getRoot().addExtraHeader("Content-Type", "application/json");
        return new Engine(root);
    }

    public final World world;

    private final ApiClient client;
    private final CoreV1Api core;
    private final String namespace;
    private final HttpNode root;

    /** Thread safe - has no fields at all */
    private final JsonParser parser;

    private Engine(HttpNode root) throws IOException {
        this.world = root.getWorld();
        this.root = root;
        this.parser = new JsonParser();

        client = Config.defaultClient();
        Configuration.setDefaultApiClient(client); // TODO: threading ...

        // client.setDebugging(true);
        core = new CoreV1Api();
        namespace = "stool";
    }

    public void close() {
        // TODO: https://github.com/kubernetes-client/java/issues/865
        client.getHttpClient().connectionPool().evictAll();

        root.getWorld().close();
    }


    //--

    public String version() throws IOException {
        return root.join("version").readString();
    }

    //-- images

    /** @return image ids mapped to ImageInfo */
    public Map<String, ImageInfo> imageList() throws IOException {
        return imageList(Collections.emptyMap());
    }

    public Map<String, ImageInfo> imageList(Map<String, String> labels) throws IOException {
        HttpNode node;
        JsonArray array;
        Map<String, ImageInfo> result;
        String id;
        JsonElement repoTags;
        List<String> repositoryTags;
        JsonObject object;
        JsonElement l;

        node = root.join("images/json");
        node = node.withParameter("all", "true");
        if (!labels.isEmpty()) {
            node = node.withParameter("filters", "{\"label\" : [" + labelsToJsonArray(labels) + "] }");
        }
        array = parser.parse(node.readString()).getAsJsonArray();
        result = new HashMap<>(array.size());
        for (JsonElement element : array) {
            object = element.getAsJsonObject();
            id = pruneImageId(object.get("Id").getAsString());
            repoTags = object.get("RepoTags");
            repositoryTags = repoTags.isJsonNull() ? new ArrayList<>() : stringList(repoTags.getAsJsonArray());
            l = object.get("Labels");
            result.put(id, new ImageInfo(id, repositoryTags, toLocalTime(object.get("Created").getAsLong()),
                    l.isJsonNull() ? new HashMap<>() : toStringMap(l.getAsJsonObject())));
        }
        return result;
    }

    private static String pruneImageId(String id) {
        return Strings.removeLeft(id, "sha256:");
    }

    private static LocalDateTime toLocalTime(long epochSeconds) {
        Instant instant = Instant.ofEpochSecond(epochSeconds);
        return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public String imageBuildWithOutput(String repositoryTag, FileNode context) throws IOException {
        try (StringWriter dest = new StringWriter()) {
            imageBuild(repositoryTag, Collections.emptyMap(), Collections.emptyMap(), context, false, dest);
            return dest.toString();
        }
    }

    /**
     * @param log may be null
     * @return image id */
    public String imageBuild(String repositoryTag, Map<String, String> args, Map<String, String> labels,
                             FileNode context, boolean noCache, Writer log) throws IOException {
        HttpNode build;
        StringBuilder output;
        JsonObject object;
        String error;
        JsonObject errorDetail;
        JsonElement value;
        AsciiInputStream in;
        String line;
        JsonElement aux;
        String id;
        FileNode tar;

        validateReference(repositoryTag);
        build = root.join("build");
        build = build.withParameter("t", repositoryTag);
        if (!labels.isEmpty()) {
            build = build.withParameter("labels", obj(labels).toString());
        }
        build = build.withParameter("buildargs", obj(args).toString());
        if (noCache) {
            build = build.withParameter("nocache", "true");
        }
        output = new StringBuilder();
        error = null;
        errorDetail = null;
        id = null;
        tar = FileNodes.tar(context);
        try {
            try (InputStream raw = postStream(build, tar)) {
                in = new AsciiInputStream(raw, 4096);
                while (true) {
                    line = in.readLine();
                    if (line == null) {
                        if (error != null) {
                            throw new BuildError(repositoryTag, error, errorDetail, output.toString());
                        }
                        if (id == null) {
                            throw new IOException("missing id");
                        }
                        return id;
                    }
                    object = parser.parse(line).getAsJsonObject();

                    eatStream(object, output, log);
                    eatString(object, "status", output, log);
                    eatString(object, "id", output, log);
                    eatString(object, "progress", output, log);
                    eatObject(object, "progressDetail", output, log);
                    aux = eatObject(object, "aux", output, log);
                    if (aux != null) {
                        if (id != null) {
                            throw new IOException("duplicate id");
                        }
                        id = pruneImageId(aux.getAsJsonObject().get("ID").getAsString());
                    }

                    value = eatString(object, "error", output, log);
                    if (value != null) {
                        if (error != null) {
                            throw new IOException("multiple errors");
                        }
                        error = value.getAsString();
                    }
                    value = eatObject(object, "errorDetail", output, log);
                    if (value != null) {
                        if (errorDetail != null) {
                            throw new IOException("multiple errors");
                        }
                        errorDetail = value.getAsJsonObject();
                    }

                    if (object.size() > 0) {
                        throw new IOException("unknown build output: " + object);
                    }
                }
            }
        } finally {
            tar.deleteFile();
        }
    }

    public JsonObject imageInspect(String id) throws IOException {
        HttpNode node;

        node = root.join("images", id, "json");
        return parser.parse(node.readString()).getAsJsonObject();
    }

    public void imageRemove(String tagOrId, boolean force) throws IOException {
        HttpNode node;

        node = root.join("images", tagOrId);
        if (force) {
            node = node.withParameter("force", "true");
        }
        Method.delete(node);
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

    public void serviceCreate(String name, int nodePort, int containerPort, String... selector) throws IOException {
        serviceCreate(name, nodePort, containerPort, Strings.toMap(selector));
    }

    public void serviceCreate(String name, int nodePort, int containerPort, Map<String, String> selector) throws IOException {
        V1ServicePort port;

        port = new V1ServicePort();
        port.setNodePort(nodePort);
        port.setPort(containerPort);
        try {
            core.createNamespacedService(namespace, new V1ServiceBuilder()
                    .withNewMetadata().withName(name).endMetadata()
                    .withNewSpec().withType("NodePort").withPorts(port).withSelector(selector).endSpec()
                    .build(), null, null, null);
        } catch (ApiException e) {
            throw wrap(e);
        }
    }

    public Map<String, ServiceInfo> serviceList() throws IOException {
        V1ServiceList list;
        Map<String, ServiceInfo> result;
        String name;
        List<V1ServicePort> ports;

        try {
            list = core.listNamespacedService(namespace, null, null, null, null, null,
                    null, null, null, null);
            result = new HashMap<>();
            for (V1Service service: list.getItems()) {
                name = service.getMetadata().getName();
                ports = service.getSpec().getPorts();
                if (ports.size() != 1) {
                    throw new IllegalStateException(ports.toString());
                }
                result.put(name, new ServiceInfo(name, ports.get(0).getNodePort(), ports.get(0).getPort()));
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

    public boolean  podCreate(String name, String image, Map<String, String> labels) throws IOException {
        return podCreate(name, image, labels, Strings.toMap());
    }

    public boolean podCreate(String name, String image, Map<String, String> labels, Map<String, String> env) throws IOException {
        return podCreate(name, image, null, false, null, labels, env, Collections.emptyMap());
    }

    public boolean podCreate(String name, String image, String hostname, boolean healing, Integer memory, Map<String, String> labels, Map<String, String> env,
                          Map<FileNode, String> mounts) throws IOException {
        String phase;

        try {
            core.createNamespacedPod(namespace, pod(name, image, hostname, healing, memory, labels, env, mounts), null, null, null);
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

    private static V1Pod pod(String name, String image, String hostname, boolean healing, Integer memory,
                             Map<String, String> labels, Map<String, String> env, Map<FileNode, String> volumes) {
        List<V1EnvVar> lst;
        V1EnvVar var;
        List<V1Volume> vl;
        V1Volume v;
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
        vname = "volumne";
        for (Map.Entry<FileNode, String> entry : volumes.entrySet()) {
            hp = new V1HostPathVolumeSource();
            hp.setPath(entry.getKey().getAbsolute());
            v = new V1Volume();
            v.setName(vname);
            v.setHostPath(hp);
            vl.add(v);
            m = new V1VolumeMount();
            m.setName(vname);
            m.setMountPath(entry.getValue());
            ml.add(m);
            vname = vname + "x";
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

    private static boolean same(String left, String right) {
        if (left == null) {
            return right == null;
        } else {
            return left.equals(right);
        }
    }

    private static IOException wrap(ApiException e) {
        return new IOException(e.getResponseBody(), e);
    }

    //-- containers

    public Map<String, ContainerInfo> containerList(String key) throws IOException {
        return doContainerList("{\"label\" : [\"" + key + "\"] }");
    }

    public Map<String, ContainerInfo> containerListForImage(String image) throws IOException {
        return doContainerList("{\"ancestor\" : [\"" + image + "\"] }");
    }

    public ContainerInfo containerInfo(String id) throws IOException {
        Map<String, ContainerInfo> map;

        map = doContainerList("{\"id\" : [\"" + id + "\"] }");
        switch (map.size()) {
            case 1:
                return map.values().iterator().next();
            default:
                throw new IllegalStateException(map.toString());
        }
    }

    private Map<String, ContainerInfo> doContainerList(String filters) throws IOException {
        HttpNode node;
        JsonArray array;
        Map<String, ContainerInfo> result;
        ContainerInfo info;

        node = root.join("containers/json");
        if (filters != null) {
            node = node.withParameter("filters", filters);
        }
        node = node.withParameter("all", "true");
        array = parser.parse(node.readString()).getAsJsonArray();
        result = new HashMap<>(array.size());
        for (JsonElement element : array) {
            info = containerInfo(element.getAsJsonObject());
            result.put(info.id, info);
        }
        return result;
    }

    private static ContainerInfo containerInfo(JsonObject object) {
        String id;
        String imageId;
        Status state; // TODO: sometimes it's called Status, sometimes state ...

        id = object.get("Id").getAsString();
        imageId = pruneImageId(object.get("ImageID").getAsString());
        state = Status.valueOf(object.get("State").getAsString().toUpperCase());
        return new ContainerInfo(id, imageId, toStringMap(object.get("Labels").getAsJsonObject()), state);
    }

    /**
     * @param memory is the memory limit in bytes. Or null for no limit. At least 1024*1024*4. The actual value used by docker is something
     *               rounded of this parameter
     * @param stopSignal or null to use default (SIGTERM)
     * @param hostname or null to not define the hostname
     * @param stopTimeout default timeout when stopping this container without explicit timeout value; null to use default (10 seconds)
     * @return container id
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public String containerCreate(String name, String image, String hostname, String networkMode, Long memory,
                                  Map<String, String> labels, Map<String, String> env, Map<FileNode, String> bindMounts, Map<Integer, String> ports) throws IOException {
        JsonObject body;
        JsonObject response;
        JsonObject hostConfig;
        JsonArray mounts;
        JsonObject portBindings;
        JsonArray drops;
        HttpNode node;

        node = root.join("containers/create");
        if (name != null) {
            node = node.withParameter("name", name);
        }
        body = object("Image", image);
        if (hostname != null) {
            body.add("Hostname", new JsonPrimitive(hostname));
        }
        if (!labels.isEmpty()) {
            body.add("Labels", obj(labels));
        }
        hostConfig = new JsonObject();

        body.add("HostConfig", hostConfig);
        if (!env.isEmpty()) {
            body.add("Env", env(env));
        }
        if (memory != null) {
            hostConfig.add("Memory", new JsonPrimitive(memory));
            // unlimited; important, because debian stretch kernel does not support this
            hostConfig.add("MemorySwap", new JsonPrimitive(-1));
        }
        if (networkMode != null) {
            hostConfig.add("NetworkMode", new JsonPrimitive(networkMode));
        }
        mounts = new JsonArray();
        hostConfig.add("Mounts", mounts);
        for (Map.Entry<FileNode, String> entry : bindMounts.entrySet()) {
            mounts.add(object("type", "bind", "source", entry.getKey().getAbsolute(), "target", entry.getValue()));
        }
        drops = new JsonArray(); // added security - not sure if I really need this
        drops.add(new JsonPrimitive("setuid"));
        drops.add(new JsonPrimitive("setgid"));
        drops.add(new JsonPrimitive("chown"));
        drops.add(new JsonPrimitive("dac_override"));
        drops.add(new JsonPrimitive("fowner"));
        drops.add(new JsonPrimitive("fsetid"));
        drops.add(new JsonPrimitive("kill"));
        drops.add(new JsonPrimitive("setpcap"));
        drops.add(new JsonPrimitive("net_bind_service"));
        drops.add(new JsonPrimitive("net_raw"));
        drops.add(new JsonPrimitive("sys_chroot"));
        drops.add(new JsonPrimitive("mknod"));
        drops.add(new JsonPrimitive("setfcap"));
        hostConfig.add("CapDrop", drops);

        portBindings = new JsonObject();
        for (Map.Entry<Integer, String> entry: ports.entrySet()) {
            portBindings.add(Integer.toString(entry.getKey()) + "/tcp", hostMapping(entry.getValue()));
        }
        hostConfig.add("PortBindings", portBindings);
        body.add("ExposedPorts", exposedPorts(ports.keySet()));

        response = post(node, body);
        checkWarnings(response);
        return response.get("Id").getAsString();
    }

    public void containerStart(String id) throws IOException {
        post(root.join("containers", id, "start"), "");
    }

    /**
     * Sends stop signal as specified containerCreate to pid 1. If process does not terminate after timeout, SIGKILL is used
     * @param timeout null to use timeout specified by containerCreate
     * */
    public void containerStop(String id, Integer timeout) throws IOException {
        HttpNode stop;

        stop = root.join("containers", id, "stop");
        if (timeout != null) {
            stop = stop.getRoot().node(stop.getPath(), "t=" + timeout);
        }
        post(stop, "");
    }

    public void containerRemove(String id) throws IOException {
        Method.delete(root.join("containers", id));
    }

    public String containerLogs(String id) throws IOException {
        final StringBuilder str;
        OutputStream dest;

        str = new StringBuilder();
        dest = new OutputStream() {
            @Override
            public void write(int b) {
                str.append((char) b);
            }
        };
        doContainerLogs(id, "stdout=1&stderr=1", dest);
        return str.toString();
    }

    public void containerLogsFollow(String id, OutputStream dest) throws IOException {
        doContainerLogs(id, "stdout=1&stderr=1&follow=1", dest);
    }

    private void doContainerLogs(String id, String options, OutputStream dest) throws IOException {
        HttpNode node;
        DataInputStream data;
        int len;

        node = root.join("containers", id, "logs");
        data = new DataInputStream(node.getRoot().node(node.getPath(), options).newInputStream());
        while (true) {
            try {
                data.readInt(); // type is ignored
            } catch (EOFException e) {
                return;
            }
            len = data.readInt();
            for (int i = 0; i < len; i++) {
                dest.write(data.readByte());
            }
        }
    }

    public int containerWait(String id) throws IOException {
        JsonObject response;

        response = post(root.join("containers", id, "wait"), object());
        return response.get("StatusCode").getAsInt();
    }

    public Status containerStatus(String id) throws IOException {
        JsonObject state;

        state = containerState(id);
        return Status.valueOf(state.get("Status").getAsString().toUpperCase());
    }

    /** @return null if container is not started */
    public Stats containerStats(String id) throws IOException {
        HttpNode node;
        JsonObject stats;
        JsonObject memory;

        node = root.join("containers", id, "stats");
        node = node.getRoot().node(node.getPath(), "stream=false");
        stats = parser.parse(node.readString()).getAsJsonObject();
        if (stats.get("cpu_stats").getAsJsonObject().get("system_cpu_usage") == null) {
            // empty default document - this is returned if that container id is invalid
            return null;
        }
        memory = stats.get("memory_stats").getAsJsonObject();
        return new Stats(cpu(stats), memory.get("usage").getAsLong(), memory.get("limit").getAsLong());
    }

    private static int cpu(JsonObject stats) {
        JsonObject current;
        JsonObject previous;
        long cpuDelta;
        long systemDelta;

        current = stats.get("cpu_stats").getAsJsonObject();
        previous = stats.get("precpu_stats").getAsJsonObject();

        cpuDelta = current.get("cpu_usage").getAsJsonObject().get("total_usage").getAsLong() - previous.get("cpu_usage").getAsJsonObject().get("total_usage").getAsLong();
        systemDelta = current.get("system_cpu_usage").getAsLong() - previous.get("system_cpu_usage").getAsLong();
        return (int) (cpuDelta * 100 / systemDelta);
    }

    private JsonObject containerState(String id) throws IOException {
        JsonObject response;
        JsonObject state;
        String error;

        response = containerInspect(id, false);
        state = response.get("State").getAsJsonObject();
        error = state.get("Error").getAsString();
        if (!error.isEmpty()) {
            throw new IOException("error state: " + error);
        }
        return state;
    }

    public JsonObject containerInspect(String id, boolean size) throws IOException {
        HttpNode node;

        node = root.join("containers", id, "json");
        if (size) {
            node = node.withParameter("size", "true");
        }
        return parser.parse(node.readString()).getAsJsonObject();
    }

    //--

    private void checkWarnings(JsonObject response) throws IOException {
        JsonElement warnings;

        warnings = response.get("Warnings");
        if (JsonNull.INSTANCE.equals(warnings)) {
            return;
        }
        if (warnings.isJsonArray()) {
            if (warnings.getAsJsonArray().size() == 0) {
                return;
            }
        }
        throw new IOException("response warnings: " + response.toString());
    }

    private JsonObject post(HttpNode dest, JsonObject obj) throws IOException {
        return parser.parse(post(dest, obj.toString() + '\n')).getAsJsonObject();
    }

    private InputStream postStream(HttpNode dest, FileNode body) throws IOException {
        try (InputStream src = body.newInputStream()) {
            return dest.postStream(new Body(null, null, body.size(), src, false));
        }
    }

    private String post(HttpNode dest, String body) throws IOException {
        try {
            return dest.post(body);
        } catch (StatusException e) {
            if (e.getStatusLine().code == 204) {
                return "";
            } else {
                throw e;
            }
        }
    }

    //--

    private void eatStream(JsonObject object, StringBuilder result, Writer log) throws IOException {
        eat(object, "stream", "", "", true, result, log);
    }

    private JsonElement eatString(JsonObject object, String key, StringBuilder result, Writer log) throws IOException {
        return eat(object, key, "[" + key + "] ", "\n", true, result, log);
    }

    private JsonElement eatObject(JsonObject object, String key, StringBuilder result, Writer log) throws IOException {
        return eat(object, key, "[" + key + "] ", "\n", false, result, log);
    }

    private JsonElement eat(JsonObject object, String key, String prefix, String suffix, boolean isString, StringBuilder result, Writer log) throws IOException {
        JsonElement value;
        String str;

        value = object.remove(key);
        if (value == null) {
            return null;
        }
        if (isString) {
            str = value.getAsString();
        } else {
            str = value.getAsJsonObject().toString();
        }
        str = prefix + str + suffix;
        result.append(str);
        if (log != null) {
            log.write(str);
        }
        return value;
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

    private static JsonObject object(Object... keyvalues) {
        JsonObject body;
        Object arg;

        if (keyvalues.length % 2 != 0) {
            throw new IllegalArgumentException();
        }
        body = new JsonObject();
        for (int i = 0; i < keyvalues.length; i += 2) {
            arg = keyvalues[i + 1];
            if (arg instanceof String) {
                arg = new JsonPrimitive((String) arg);
            } else if (arg instanceof Number) {
                arg = new JsonPrimitive((Number) arg);
            } else if (arg instanceof Boolean) {
                arg = new JsonPrimitive((Boolean) arg);
            }
            body.add((String) keyvalues[i], (JsonElement) arg);
        }
        return body;
    }

    private static String labelsToJsonArray(Map<String, String> map) {
        StringBuilder builder;

        builder = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append('"');
            builder.append(entry.getKey());
            builder.append('=');
            builder.append(entry.getValue());
            builder.append('"');
        }
        return builder.toString();
    }

    public static JsonObject obj(Map<String, String> obj) {
        JsonObject result;

        result = new JsonObject();
        for (Map.Entry<String, String> entry : obj.entrySet()) {
            result.add(entry.getKey(), new JsonPrimitive(entry.getValue()));
        }
        return result;
    }

    private static List<String> stringList(JsonArray array) {
        List<String> result;

        result = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            result.add(element.getAsString());
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

    private static JsonArray env(Map<String, String> env) {
        JsonArray result;

        result = new JsonArray();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            result.add(entry.getKey() + "=" + entry.getValue());
        }
        return result;
    }

    private static JsonObject exposedPorts(Set<Integer> ports) {
        JsonObject obj;

        obj = new JsonObject();
        for (Integer port : ports) {
            obj.add(Integer.toString(port) + "/tcp", new JsonObject());
        }
        return obj;
    }

    private static JsonArray hostMapping(String ipOptPort) {
        int idx;
        String ip;
        int port;
        JsonArray result;
        JsonObject obj;

        idx = ipOptPort.indexOf(':');
        if (idx == -1) {
            ip = null;
            port = Integer.parseInt(ipOptPort);
        } else {
            ip = ipOptPort.substring(0, idx);
            port = Integer.parseInt(ipOptPort.substring(idx +1));
        }
        obj = new JsonObject();
        if (ip != null) {
            obj.add("HostIp", new JsonPrimitive(ip));

        }
        obj.add("HostPort", new JsonPrimitive(Integer.toString(port)));
        result = new JsonArray();
        result.add(obj);
        return result;
    }
}
