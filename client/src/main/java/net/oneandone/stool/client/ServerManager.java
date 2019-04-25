package net.oneandone.stool.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.oneandone.inline.ArgumentException;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerManager {

    private final FileNode file;

    public final FileNode wirelog;
    public final String clientInvocation;
    public final String clientCommand;
    public final Map<String, Server> servers;

    public ServerManager(FileNode file, FileNode wirelog, String clientInvocation, String clientCommand) {
        this.file = file;
        this.wirelog = wirelog;
        this.clientInvocation = clientInvocation;
        this.clientCommand = clientCommand;
        this.servers = new HashMap<>();
    }

    public void add(String name, String url) {
        servers.put(name, new Server(name, url, null, null, clientInvocation, clientCommand));
    }

    public Reference reference(String str) throws IOException {
        int idx;
        String server;

        idx = str.indexOf('@');
        if (idx == -1) {
            throw new IllegalArgumentException(str);
        }
        server = str.substring(idx + 1);
        return new Reference(get(server).connect(file.getWorld()), str.substring(0, idx));
    }

    public Server lookup(String server) {
        return servers.get(server);
    }

    public Server get(String server) {
        Server result;

        result = lookup(server);
        if (result == null) {
            throw new ArgumentException("unknown server: " + server);
        }
        return result;
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
                client = server.connect(file.getWorld());
                result.addAll(Reference.list(client, client.list(clientFilter)));
            }
        }
        return result;
    }

    public void load() throws IOException {
        JsonArray array;
        Server server;

        servers.clear();

        array = new JsonParser().parse(file.readString()).getAsJsonArray();
        for (JsonElement element : array) {
            server = Server.fromJson(element.getAsJsonObject(), wirelog, clientInvocation, clientCommand);
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
}
