package net.oneandone.stool.docker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;
import net.oneandone.sushi.fs.Settings;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.http.HttpFilesystem;
import net.oneandone.sushi.fs.http.HttpNode;
import net.oneandone.sushi.fs.http.StatusException;
import net.oneandone.sushi.fs.http.model.Method;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarHeader;
import org.kamranzafar.jtar.TarOutputStream;

import javax.net.SocketFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

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
        root = (HttpNode) world.validNode("http://localhost/v1.30");
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


    public String build(String name, String dockerfile) throws IOException {
        Settings settings;
        HttpNode node;
        ByteArrayOutputStream dest;
        TarOutputStream tar;
        byte[] dockerfileBytes;

        node = root.join("build");
        node = node.getRoot().node(node.getPath(), "t=" + name);

        settings = root.getWorld().getSettings();
        dockerfileBytes = settings.bytes(dockerfile);
        dest = new ByteArrayOutputStream(dockerfileBytes.length + 100);
        tar = new TarOutputStream(dest);
        tar.putNextEntry(new TarEntry(TarHeader.createHeader("Dockerfile", (long) dockerfileBytes.length, System.currentTimeMillis(), false)));
        try (InputStream in = new ByteArrayInputStream(dockerfileBytes)) {
            root.getWorld().getBuffer().copy(in, tar);
        }
        tar.close();
        return settings.string(post(node, dest.toByteArray()));
    }

    public void imageRemove(String id) throws IOException {
        Method.delete(root.join("images", id));
    }


    //-- containers

    public String containerCreate(String image) throws IOException {
        return containerCreate(image, Collections.emptyMap(), Collections.emptyMap());
    }

    public String containerCreate(String image, Map<String, String> bindMounts, Map<Integer, Integer> ports) throws IOException {
        JsonObject body;
        JsonObject response;
        JsonObject hostConfig;
        JsonArray binds;
        JsonObject portBindings;

        body = body("Image", image);

        hostConfig = new JsonObject();

        body.add("HostConfig", hostConfig);
        binds = new JsonArray();
        hostConfig.add("Binds", binds);
        for (Map.Entry<String, String> entry : bindMounts.entrySet()) {
            binds.add(entry.getKey() + ":" + entry.getValue());
        }

        portBindings = new JsonObject();
        for (Map.Entry<Integer, Integer> entry: ports.entrySet()) {
            portBindings.add(Integer.toString(entry.getKey()) + "/tcp", hp(entry.getValue()));
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

    private static JsonArray hp(int port) {
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

    public String containerStartedAt(String id) throws IOException {
        JsonObject state;

        state = containerState(id);
        return state.get("StartedAt").getAsString();
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

    private byte[] post(HttpNode dest, byte[] str) throws IOException {
        try {
            return dest.post(str);
        } catch (StatusException e) {
            if (e.getStatusLine().code == 204) {
                return new byte[0];
            } else {
                throw e;
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
