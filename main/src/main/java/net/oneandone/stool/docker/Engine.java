package net.oneandone.stool.docker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;
import net.oneandone.sushi.fs.FileNotFoundException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.HttpFilesystem;
import net.oneandone.sushi.fs.http.HttpNode;
import net.oneandone.sushi.fs.http.MovedTemporarilyException;
import net.oneandone.sushi.fs.http.StatusException;
import net.oneandone.sushi.fs.http.io.AsciiInputStream;
import net.oneandone.sushi.fs.http.model.Body;
import net.oneandone.sushi.fs.http.model.Method;
import net.oneandone.sushi.fs.http.model.Request;
import net.oneandone.sushi.fs.http.model.Response;
import net.oneandone.sushi.fs.http.model.StatusCode;
import net.oneandone.sushi.io.LineFormat;
import net.oneandone.sushi.io.LineReader;
import net.oneandone.sushi.util.Separator;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarHeader;
import org.kamranzafar.jtar.TarOutputStream;

import javax.net.SocketFactory;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Connect to local docker engine via unix socket. https://docs.docker.com/engine/api/v1.33/ */
public class Engine {
    public enum Status {
        CREATED,
        RUNNING,
        EXITED,
    }

    public static Engine open(String wirelog) throws IOException {
        World world;
        HttpFilesystem fs;
        HttpNode root;

        world = World.create();
        if (wirelog != null) {
            HttpFilesystem.wireLog(wirelog);
        }
        fs = (HttpFilesystem) world.getFilesystem("http");
        fs.setSocketFactorySelector(Engine::unixSocketFactorySelector);
        root = (HttpNode) world.validNode("http://localhost/v1.33");
        root.getRoot().addExtraHeader("Content-Type", "application/json");
        return new Engine(root);
    }

    private final HttpNode root;
    private final JsonParser parser;

    public Engine(HttpNode root) {
        this.root = root;
        this.parser = new JsonParser();
    }

    //--

    public String version() throws IOException {
        return root.join("version").readString();
    }

    //-- images


    /** @return build output */
    public String imageBuild(String name, FileNode context) throws IOException {
        HttpNode node;
        StringBuilder result;
        JsonObject object;
        JsonObject error;
        String key;
        JsonElement code;
        AsciiInputStream in;
        String line;

        node = root.join("build");
        node = node.getRoot().node(node.getPath(), "t=" + name);
        result = new StringBuilder();
        error = null;
        try (InputStream raw = postStream(node, tar(context))) {
            // TODO: hangs if I use as InputStreamReader instead ...
            in = new AsciiInputStream(raw, 4096);
            while (true) {
                line = in.readLine();
                if (line == null) {
                    if (error != null) {
                        code = error.get("code");
                        throw new BuildError(code != null ? code.getAsInt() : -1, error.get("message").getAsString(), result.toString());
                    }
                    return result.toString();
                }
                object = parser.parse(line).getAsJsonObject();
                key = getKey(object);
                switch (key) {
                    case "aux":
                        // image id, currently not used
                        break;
                    case "stream":
                        result.append(object.get(key).getAsString());
                        break;
                    case "error":
                        if (error != null) {
                            throw new IOException("multiple errors");
                        }
                        error = object.get("errorDetail").getAsJsonObject();
                        break;
                    default:
                        throw new IOException("unknown docker response key '" + key + "' in object " + object);
                }
            }

        }
    }

    private static String getKey(JsonObject object) throws IOException {
        JsonElement key;

        switch (object.size()) {
            case 1:
                return object.entrySet().iterator().next().getKey();
            case 2:
                key = object.get("error");
                if (key != null) {
                    return "error";
                }
                key = object.get("status");
                if (key != null) {
                    return "status";
                }
                // fall-through
            default:
                throw new IOException("unexpected object: " + object);
        }
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

    public void imageRemove(String id) throws IOException {
        Method.delete(root.join("images", id));
    }


    //-- containers

    public String containerCreate(String image, String hostname) throws IOException {
        return containerCreate(image, hostname, 0, Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * @param memory is the memory limit in bytes. At least 1024*1024*4. The actual value used by docker is something
     *               rounded of this parameter
     * @return container id
     */
    public String containerCreate(String image, String hostname, int memory, Map<String, String> bindMounts, Map<Integer, Integer> ports) throws IOException {
        JsonObject body;
        JsonObject response;
        JsonObject hostConfig;
        JsonArray binds;
        JsonObject portBindings;

        body = body("Image", image, "hostname", hostname);

        hostConfig = new JsonObject();

        body.add("HostConfig", hostConfig);
        hostConfig.add("Memory", new JsonPrimitive(memory));
        binds = new JsonArray();
        hostConfig.add("Binds", binds);
        for (Map.Entry<String, String> entry : bindMounts.entrySet()) {
            binds.add(entry.getKey() + ":" + entry.getValue());
        }

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

    public void containerStop(String id) throws IOException {
        post(root.join("containers", id, "stop"), "");
    }

    public void containerRemove(String id) throws IOException {
        Method.delete(root.join("containers", id));
    }

    public String containerLogs(String id) throws IOException {
        HttpNode node;

        node = root.join("containers", id, "logs");
        return node.getRoot().node(node.getPath(), "stdout=1").readString();
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

    public Stats containerStats(String id) throws IOException {
        HttpNode node;
        JsonObject stats;
        JsonObject memory;

        node = root.join("containers", id, "stats");
        node = node.getRoot().node(node.getPath(), "stream=false");
        stats = parser.parse(node.readString()).getAsJsonObject();
        memory = stats.get("memory_stats").getAsJsonObject();
        return new Stats(cpu(stats), memory.get("usage").getAsInt(), memory.get("limit").getAsInt());
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

        response = parser.parse(root.join("containers", id, "json").readString()).getAsJsonObject();
        state = response.get("State").getAsJsonObject();
        error = state.get("Error").getAsString();
        if (!error.isEmpty()) {
            throw new IOException("error state: " + error);
        }
        return state;
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
        return postStream(dest, new Body(null, null, body.length, new ByteArrayInputStream(body), false));
    }

    // TODO: move to sushi
    private static InputStream postStream(HttpNode resource, Body body) throws IOException {
        Request post;
        Response response;

        post = new Request("POST", resource);
        post.bodyHeader(body);
        response = post.responseHeader(post.open(body));
        if (response.getStatusLine().code == StatusCode.OK) {
            return new FilterInputStream(response.getBody().content) {
                private boolean freed = false;

                @Override
                public void close() throws IOException {
                    if (!freed) {
                        freed = true;
                        post.free(response);
                    }
                    super.close();
                }
            };
        } else {
            post.free(response);
            switch (response.getStatusLine().code) {
                case StatusCode.MOVED_TEMPORARILY:
                    throw new MovedTemporarilyException(response.getHeaderList().getFirstValue("Location"));
                case StatusCode.NOT_FOUND:
                case StatusCode.GONE:
                case StatusCode.MOVED_PERMANENTLY:
                    throw new FileNotFoundException(resource);
                default:
                    throw StatusException.forResponse(response);
            }
        }
    }


    private String post(HttpNode dest, String str) throws IOException {
        try {
            return dest.post(str);
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

    //--

    private static SocketFactory unixSocketFactorySelector(String protocol, String hostname) {
        return new SocketFactory() {
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

                address = new UnixSocketAddress(new File("/var/run/docker.sock"));
                return UnixSocketChannel.open(address).socket();
            }
        };
    }
}
