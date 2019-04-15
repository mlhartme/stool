package net.oneandone.stool.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Servers {
    private static class Server {
        public static Server fromJson(JsonObject obj) {
            return new Server(obj.get("name").getAsString(), obj.get("url").getAsString(), obj.get("token").getAsString());
        }

        public final String name;
        public final String url;
        public final String token;

        private Server(String name, String url, String token) {
            this.name = name;
            this.url = url;
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

    public Servers(FileNode file, FileNode wirelog, String clientInvocation, String clientCommand) {
        this.file = file;
        this.wirelog = wirelog;
        this.clientInvocation = clientInvocation;
        this.clientCommand = clientCommand;
        this.servers = new HashMap<>();
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
        return Client.token(file.getWorld(), server.url, wirelog, clientInvocation, clientCommand, server.token);
    }
}
