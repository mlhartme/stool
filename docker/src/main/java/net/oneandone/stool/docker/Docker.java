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
package net.oneandone.stool.docker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;
import net.oneandone.inline.ArgumentException;
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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Connect to local docker engine via unix socket. https://docs.docker.com/engine/api/v1.37/
 * Not thread-safe because the io buffer is shared.
 */
public class Docker implements AutoCloseable {
    public enum Status {
        CREATED,
        RUNNING,
        EXITED,
        REMOVING /* not used in my code, but docker engine documentation says it can be returned */
    }

    public static Docker create() throws IOException {
        return create(null);
    }

    public static Docker create(String wirelog) throws IOException {
        return create("/var/run/docker.sock", wirelog);
    }

    public static Docker create(String socketPath, String wirelog) throws IOException {
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
        return new Docker(root);
    }

    public final World world;
    private final HttpNode root;

    /** Thread safe - has no fields at all */
    private final JsonParser parser;

    private Docker(HttpNode root) {
        this.world = root.getWorld();
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

    //--

    private static boolean same(String left, String right) {
        if (left == null) {
            return right == null;
        } else {
            return left.equals(right);
        }
    }

    //-- containers

    public Map<String, ContainerInfo> containerList(String key) throws IOException {
        return doContainerList("{\"label\" : [\"" + key + "\"] }");
    }

    public Map<String, ContainerInfo> containerListForImage(String image) throws IOException {
        return doContainerList("{\"ancestor\" : [\"" + image + "\"] }");
    }

    public ContainerInfo containerInfo(String id) throws IOException {
        ContainerInfo result;

        result = containerInfoOpt(id);
        if (result == null) {
            throw new IOException("container not found: " + id);
        }
        return result;
    }

    public ContainerInfo containerInfoOpt(String id) throws IOException {
        Map<String, ContainerInfo> map;

        map = doContainerList("{\"id\" : [\"" + id + "\"] }");
        switch (map.size()) {
            case 1:
                return map.values().iterator().next();
            default:
                return null;
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
        return new ContainerInfo(id, imageId, state);
    }

    //--

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
}
