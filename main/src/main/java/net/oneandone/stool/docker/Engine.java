package net.oneandone.stool.docker;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.http.HttpFilesystem;
import net.oneandone.sushi.fs.http.HttpNode;
import net.oneandone.sushi.fs.http.StatusException;

import javax.net.SocketFactory;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URISyntaxException;

public class Engine {
    public static void main(String[] args) throws IOException, URISyntaxException {
        Engine engine;
        String id;

        engine = Engine.create();
        System.out.println("version: " + engine.version());
        id = engine.containerCreate("hello-world");
        System.out.println("create: " + id);
        engine.containerStart(id);
        System.out.println("started");
        System.out.println("wait:" + engine.containerWait(id));
        System.out.println("logs" + engine.containerLogs(id));
    }

    public static Engine create() throws IOException {
        World world;
        HttpFilesystem fs;
        HttpNode root;

        world = World.create();
        HttpFilesystem.wireLog("wire.log");
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

    public String version() throws IOException {
        return root.join("version").readString();
    }

    public String containerCreate(String image) throws IOException {
        JsonObject response;

        response = post(root.join("containers/create"), body("Image", image));
        checWarnings(response);
        return response.get("Id").getAsString();
    }

    public void containerStart(String id) throws IOException {
        post(root.join("containers", id, "start"), "");
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

    //--

    private void checWarnings(JsonObject response) throws IOException {
        if (!JsonNull.INSTANCE.equals(response.get("Warnings"))) {
            throw new IOException("response warnings: " + response.toString());
        }
    }

    private JsonObject post(HttpNode dest, JsonObject obj) throws IOException {
        return parser.parse(post(dest, obj.toString() + '\n')).getAsJsonObject();
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
