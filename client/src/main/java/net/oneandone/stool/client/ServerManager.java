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
package net.oneandone.stool.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.oneandone.inline.ArgumentException;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ServerManager implements Iterable<Server> {
    public final FileNode file;

    public final FileNode wirelog;
    public final String clientInvocation;
    public final String clientCommand;
    public final Map<String, Server> servers;

    public ServerManager(FileNode file) {
        this(file, null, null, null);
    }

    public ServerManager(FileNode file, FileNode wirelog, String clientInvocation, String clientCommand) {
        this.file = file;
        this.wirelog = wirelog;
        this.clientInvocation = clientInvocation;
        this.clientCommand = clientCommand;
        this.servers = new HashMap<>();
    }

    public boolean isEmpty() {
        return servers.isEmpty();
    }

    public void add(String name, String url, String token) {
        servers.put(name, new Server(name, url, token, null, clientInvocation, clientCommand));
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
            if (serverFilter.isEmpty() || server.name.toLowerCase().equals(serverFilter.toLowerCase())) {
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

    public void save(Gson gson) throws IOException {
        JsonArray array;

        array = new JsonArray();
        for (Server server : servers.values()) {
            array.add(server.toJson());
        }
        try (Writer writer = file.newWriter()) {
            gson.toJson(array, writer);
        }
    }

    public boolean needAuthentication() {
        for (Server server : servers.values()) {
            if (server.hasToken()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterator<Server> iterator() {
        return Collections.unmodifiableCollection(servers.values()).iterator();
    }
}
