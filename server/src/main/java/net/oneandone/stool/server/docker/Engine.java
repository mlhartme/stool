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
package net.oneandone.stool.server.docker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import jnr.posix.POSIXFactory;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.HttpFilesystem;
import net.oneandone.sushi.fs.http.HttpNode;
import net.oneandone.sushi.fs.http.StatusException;
import net.oneandone.sushi.fs.http.io.AsciiInputStream;
import net.oneandone.sushi.fs.http.model.Body;
import net.oneandone.sushi.fs.http.model.Method;
import net.oneandone.sushi.util.Strings;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarHeader;
import org.kamranzafar.jtar.TarOutputStream;

import javax.net.SocketFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Connect to local docker engine via unix socket. https://docs.docker.com/engine/api/v1.37/ */
public class Engine implements AutoCloseable {
    public static final DateTimeFormatter CREATED_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.n'Z'");

    public enum Status {
        CREATED,
        RUNNING,
        EXITED,
        REMOVING
    }

    public static Engine open(String socketPath, String wirelog) throws IOException {
        World world;
        HttpFilesystem fs;
        HttpNode root;

        // CAUTION: local World because I need a special socket factory
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
        root = (HttpNode) world.validNode("http://localhost/v1.39");
        root.getRoot().addExtraHeader("Content-Type", "application/json");
        return new Engine(root);
    }

    private final HttpNode root;
    private final JsonParser parser;

    public Engine(HttpNode root) {
        this.root = root;
        this.parser = new JsonParser();
    }

    public void close() {
        root.getWorld().close();
    }


    //--

    public String version() throws IOException {
        return root.join("version").readString();
    }

    //-- images


    public static class ImageListInfo {
        public final String id;
        public final List<String> tags;

        public ImageListInfo(String id, List<String> tags) {
            this.id = id;
            this.tags = tags;
        }
    }

    /** @return image ids */
    public Map<String, ImageListInfo> imageList() throws IOException {
        return imageList(Collections.emptyMap());

    }

    public Map<String, ImageListInfo> imageList(Map<String, String> labels) throws IOException {
        HttpNode node;
        JsonArray array;
        Map<String, ImageListInfo> result;
        String id;
        List<String> tags;

        node = root.join("images/json");
        node = node.withParameter("all", "true");
        if (!labels.isEmpty()) {
            node = node.withParameter("filters", "{\"label\" : [" + labelsToJsonArray(labels) + "] }");
        }
        array = parser.parse(node.readString()).getAsJsonArray();
        result = new HashMap<>(array.size());
        for (JsonElement element : array) {
            id = element.getAsJsonObject().get("Id").getAsString();
            id = Strings.removeLeft(id, "sha256:");
            tags = stringList(element.getAsJsonObject().get("RepoTags").getAsJsonArray());
            result.put(id, new ImageListInfo(id, tags));
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

    public static class ContainerListInfo {
        public final String id;
        public final String imageId;
        public final Map<String, String> labels;
        public final Map<Integer, Integer> ports;

        public ContainerListInfo(String id, String imageId, Map<String, String> labels, Map<Integer, Integer> ports) {
            this.id = id;
            this.imageId = imageId;
            this.labels = labels;
            this.ports = ports;
        }
    }

    /**
     * @param image may be null
     * @return container ids
     */
    public Map<String, ContainerListInfo> containerListForImage(String image) throws IOException {
        return doContainerList("{\"ancestor\" : [\"" + image + "\"] }", true);
    }

    public Map<String, ContainerListInfo> containerListRunning(String key, String value) throws IOException {
        return doContainerList("{\"label\" : [\"" + key + "=" + value + "\"], \"status\" : [\"running\"] }", false);
    }

    public Map<String, ContainerListInfo> containerList(String key, String value) throws IOException {
        return doContainerList("{\"label\" : [\"" + key + "=" + value + "\"] }", true);
    }
    public Map<String, ContainerListInfo> containerListRunning(String key) throws IOException {
        return doContainerList("{\"label\" : [\"" + key + "\"], \"status\" : [\"running\"] }", false);
    }
    public Map<String, ContainerListInfo> containerList(String key) throws IOException {
        return doContainerList("{\"label\" : [\"" + key + "\"] }", true);
    }

    private Map<String, ContainerListInfo> doContainerList(String filters, boolean all) throws IOException {
        HttpNode node;
        JsonArray array;
        Map<String, ContainerListInfo> result;
        JsonObject object;
        String id;
        String imageId;
        Map<String, String> labels;
        String app;

        node = root.join("containers/json");
        if (filters != null) {
            node = node.withParameter("filters", filters);
        }
        if (all) {
            node = node.withParameter("all", "true");
        }
        array = parser.parse(node.readString()).getAsJsonArray();
        result = new HashMap<>(array.size());
        for (JsonElement element : array) {
            object = element.getAsJsonObject();
            id = object.get("Id").getAsString();
            imageId = object.get("ImageID").getAsString();
            result.put(id, new ContainerListInfo(id, imageId, toStringMap(object.get("Labels").getAsJsonObject()), ports(element.getAsJsonObject().get("Ports").getAsJsonArray())));
        }
        return result;
    }

    private static Map<String, String> toStringMap(JsonObject obj) {
        Map<String, String> result;

        result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    private static Map<Integer, Integer> ports(JsonArray array) {
        JsonObject obj;
        Map<Integer, Integer> ports;

        ports = new HashMap<>();
        for (JsonElement element : array) {
            obj = element.getAsJsonObject();
            ports.put(obj.get("PrivatePort").getAsInt(), obj.get("PublicPort").getAsInt());
        }
        return ports;
    }

    /** @return output */
    public String imageBuildWithOutput(String nameTag, FileNode context) throws IOException {
        try (StringWriter dest = new StringWriter()) {
            imageBuild(nameTag, Collections.emptyMap(), Collections.emptyMap(), context, false, dest);
            return dest.toString();
        }
    }

    /**
     * @param log may be null
     * @return image id */
    public String imageBuild(String nameTag, Map<String, String> args, Map<String, String> labels,
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

        build = root.join("build");
        build = build.withParameter("t", nameTag);
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
        try (InputStream raw = postStream(build, tar(context))) {
            in = new AsciiInputStream(raw, 4096);
            while (true) {
                line = in.readLine();
                if (line == null) {
                    if (error != null) {
                        throw new BuildError(nameTag, error, errorDetail, output.toString());
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
                    id = Strings.removeLeft(aux.getAsJsonObject().get("ID").getAsString(), "sha256:");
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
    }

    private void eatStream(JsonObject object, StringBuilder result, Writer log) throws IOException {
        eat(object, "stream", "", "", true, result, log);
    }

    private JsonElement eatString(JsonObject object, String key, StringBuilder result, Writer log) throws IOException {
        return eat(object, key, "[" + key + "] ", "\n", true, result, log);
    }

    private JsonElement eatObject(JsonObject object, String key, StringBuilder result, Writer log) throws IOException {
        return eat(object, key, "[" + key + "] ", "\n",false, result, log);
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

    /** tar directory into byte array */
    private byte[] tar(FileNode context) throws IOException {
        List<FileNode> all;
        ByteArrayOutputStream dest;
        TarOutputStream tar;
        byte[] bytes;
        Iterator<FileNode> iter;
        FileNode file;
        long now;

        dest = new ByteArrayOutputStream();
        tar = new TarOutputStream(dest);
        now = System.currentTimeMillis();
        all = context.find("**/*");
        iter = all.iterator();
        while (iter.hasNext()) {
            file = iter.next();
            if (file.isDirectory()) {
                tar.putNextEntry(new TarEntry(TarHeader.createHeader(file.getRelative(context), 0, now, true, 0700)));
                iter.remove();
            }
        }
        iter = all.iterator();
        while (iter.hasNext()) {
            file = iter.next();
            bytes = file.readBytes();
            tar.putNextEntry(new TarEntry(TarHeader.createHeader(file.getRelative(context), bytes.length, now, false, 0700)));
            tar.write(bytes);
        }
        tar.close();
        return dest.toByteArray();
    }

    public void imageRemove(String tagOrId, boolean force) throws IOException {
        HttpNode node;

        node = root.join("images", tagOrId);
        if (force) {
            node = node.withParameter("force", "true");
        }
        Method.delete(node);
    }


    //-- containers

    public String containerCreate(String image, String hostname) throws IOException {
        return containerCreate(image, hostname, false, null, null, null,
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * @param memory is the memory limit in bytes. Or null for no limit. At least 1024*1024*4. The actual value used by docker is something
     *               rounded of this parameter
     * @param stopSignal or null to use default (SIGTERM)
     * @param stopTimeout default timeout when stopping this container without explicit timeout value; null to use default (10 seconds)
     * @return container id
     */
    public String containerCreate(String image, String hostname, boolean priviledged, Long memory, String stopSignal, Integer stopTimeout,
                                  Map<String, String> labels, Map<String, String> env, Map<FileNode, String> bindMounts, Map<Integer, Integer> ports) throws IOException {
        JsonObject body;
        JsonObject response;
        JsonObject hostConfig;
        JsonArray mounts;
        JsonObject portBindings;
        JsonArray drops;

        if (priviledged) {
            body = body("Image", image, "Hostname", hostname);
        } else {
            body = body("Image", image, "Hostname", hostname, "User", Long.toString(geteuid()), "Group", Long.toString(getegid()));
        }
        if (!labels.isEmpty()) {
            body.add("Labels", obj(labels));
        }
        if (stopSignal != null) {
            body.add("StopSignal", new JsonPrimitive(stopSignal));
        }
        if (stopTimeout != null) {
            body.add("StopTimeout", new JsonPrimitive(stopTimeout));
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
        if (priviledged) {
            hostConfig.add("Privileged", new JsonPrimitive(true));
        }
        mounts = new JsonArray();
        hostConfig.add("Mounts", mounts);
        for (Map.Entry<FileNode, String> entry : bindMounts.entrySet()) {
            mounts.add(body("type", "bind", "source", entry.getKey().checkExists().getAbsolute(), "target", entry.getValue()));
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
        for (Map.Entry<Integer, Integer> entry: ports.entrySet()) {
            portBindings.add(Integer.toString(entry.getKey()) + "/tcp", hostPort(entry.getValue()));
        }
        hostConfig.add("PortBindings", portBindings);
        body.add("ExposedPorts", exposedPorts(ports.keySet()));

        response = post(root.join("containers/create"), body);
        checWarnings(response);
        return response.get("Id").getAsString();
    }

    private static JsonObject fuseDevice() { // https://gist.github.com/dims/0d1ac1a5598e0b8a72e0
        JsonObject result;

        result = new JsonObject();
        result.add("PathOnHost", new JsonPrimitive("/dev/fuse"));
        result.add("PathInContainer", new JsonPrimitive("/dev/fuse"));
        result.add("CgroupPermissions", new JsonPrimitive("mrw"));
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

    private static JsonArray hostPort(int port) {
        JsonArray result;
        JsonObject obj;

        obj = new JsonObject();
        obj.add("HostPort", new JsonPrimitive(Integer.toString(port)));
        result = new JsonArray();
        result.add(obj);
        return result;
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

        response = post(root.join("containers", id, "wait"), body());
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

    // https://github.com/moby/moby/pull/15010
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.n'Z'");

    public long containerStartedAt(String id) throws IOException {
        JsonObject state;
        String str;
        LocalDateTime result;

        state = containerState(id);
        str = state.get("StartedAt").getAsString();
        try {
            result = LocalDateTime.parse(str, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IOException("cannot parse date: " + str);
        }
        // CAUTION: container executes in GMT timezone
        return result.atZone(ZoneId.of("GMT")).toInstant().toEpochMilli();
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

    public Map<String, String> imageLabels(String id) throws IOException {
        JsonObject response;
        JsonObject labels;
        Map<String, String> result;

        result = new HashMap<>();
        response = imageInspect(id);
        labels = response.get("Config").getAsJsonObject().get("Labels").getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : labels.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    public JsonObject imageInspect(String id) throws IOException {
        HttpNode node;

        node = root.join("images", id, "json");
        return parser.parse(node.readString()).getAsJsonObject();
    }

    //--

    private void checWarnings(JsonObject response) throws IOException {
        if (!JsonNull.INSTANCE.equals(response.get("Warnings"))) {
            throw new IOException("response warnings: " + response.toString());
        }
    }

    private JsonObject post(HttpNode dest, JsonObject obj) throws IOException {
        return parser.parse(post(dest, obj.toString() + '\n')).getAsJsonObject();
    }

    private InputStream postStream(HttpNode dest, byte[] body) throws IOException {
        return dest.postStream(new Body(null, null, body.length, new ByteArrayInputStream(body), false));
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

    private static JsonObject body(Object... keyvalues) {
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

    private static String enc(String str) {
        try {
            return URLEncoder.encode(str, "utf8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonObject obj(Map<String, String> obj) {
        JsonObject result;

        result = new JsonObject();
        for (Map.Entry<String, String> entry : obj.entrySet()) {
            result.add(entry.getKey(), new JsonPrimitive(entry.getValue()));
        }
        return result;
    }

    //--

    private static final jnr.posix.POSIX POSIX = POSIXFactory.getPOSIX();

    public static int geteuid() {
        return POSIX.geteuid();
    }

    public static int getegid() {
        return POSIX.getegid();
    }
}
