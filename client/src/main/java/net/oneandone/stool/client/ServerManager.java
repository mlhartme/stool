package net.oneandone.stool.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.oneandone.inline.ArgumentException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerManager {
    private static class Server {
        public static Server fromJson(JsonObject obj) {
            return new Server(obj.get("name").getAsString(), obj.get("url").getAsString(), obj.get("token").getAsString());
        }

        public final String name;
        public final String url;
        private String token;

        private Server(String name, String url, String token) {
            this.name = name;
            this.url = url;
            this.token = token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public JsonObject toJson() {
            JsonObject result;

            result = new JsonObject();
            result.add("name", new JsonPrimitive(name));
            result.add("url", new JsonPrimitive(url));
            result.add("token", new JsonPrimitive(token));
            return result;
        }
    }

    private final FileNode file;
    private final FileNode wirelog;
    private final String clientInvocation;
    private final String clientCommand;
    private final Map<String, Server> servers;

    public ServerManager(FileNode file, FileNode wirelog, String clientInvocation, String clientCommand) {
        this.file = file;
        this.wirelog = wirelog;
        this.clientInvocation = clientInvocation;
        this.clientCommand = clientCommand;
        this.servers = new HashMap<>();
    }

    public void auth(String serverName, String username, String password) throws IOException {
        World world;
        Server server;
        Client client;
        String token;

        world = file.getWorld();
        server = servers.get(serverName);
        if (server == null) {
            throw new ArgumentException("unknown server: " + serverName);
        }
        client = Client.basicAuth(world, server.name, server.url, wirelog, clientInvocation, clientCommand, username, password);
        token = client.auth();
        server.setToken(token);
        save();
    }

    public Reference reference(String str) throws IOException {
        int idx;
        String name;


        idx = str.indexOf('@');
        if (idx == -1) {
            throw new IllegalArgumentException(str);
        }
        name = str.substring(idx + 1);
        if (servers.get(name) == null) {
            throw new IllegalArgumentException("unknown server: " + name);
        }
        return new Reference(client(name), str.substring(0, idx));
    }

    public List<Reference> list(String filter) throws IOException {
        int idx;
        List<Reference> result;
        Client client;
        String clientFilter;
        String serverFilter;

        if (filter == null) {
            clientFilter = null;
            serverFilter = "";
        } else {
            idx = filter.lastIndexOf('@');
            if (idx == -1) {
                clientFilter = filter;
                serverFilter = "";
            } else {
                clientFilter = filter.substring(0, idx);
                serverFilter = filter.substring(idx + 1);
            }
        }
        result = new ArrayList<>();
        for (Server server : servers.values()) {
            if (server.name.toLowerCase().contains(serverFilter.toLowerCase())) {
                client = client(server.name);
                result.addAll(Reference.list(client, client.list(clientFilter)));
            }
        }
        return result;
    }

    public void setDefaults() {
        String name;

        name = "default";
        servers.clear();
        servers.put(name, new Server(name, "http://localhost:8080/api", ""));
    }

    public void load() throws IOException {
        JsonArray array;
        Server server;

        servers.clear();

        array = new JsonParser().parse(file.readString()).getAsJsonArray();
        for (JsonElement element : array) {
            server = Server.fromJson(element.getAsJsonObject());
            servers.put(server.name, server);
        }
    }

    public void save() throws IOException {
        JsonArray array;

        array = new JsonArray();
        for (Server server : servers.values()) {
            array.add(server.toJson());
        }
        file.writeString(array.toString());
    }

    public Client client(String name) throws IOException {
        Server server;

        server = servers.get(name);
        if (server == null) {
            throw new IOException("unknown server: " + name + "\nknown servers: " + servers.keySet());
        }
        return Client.token(file.getWorld(), server.name, server.url, wirelog, clientInvocation, clientCommand, server.token);
    }
}
