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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.oneandone.inline.ArgumentException;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Configuration {
    public final FileNode file;


    private String version;
    private String registryPrefix;
    public final FileNode wirelog;
    public final String clientInvocation;
    public final String clientCommand;
    public final Map<String, Server> servers;

    public Configuration(FileNode file) {
        this(file, null, null, null);
    }

    public Configuration(FileNode file, FileNode wirelog, String clientInvocation, String clientCommand) {
        this.file = file;
        this.version = null;
        this.registryPrefix = "127.0.0.1:31500/";
        this.wirelog = wirelog;
        this.clientInvocation = clientInvocation;
        this.clientCommand = clientCommand;
        this.servers = new LinkedHashMap<>();
    }

    public boolean isEmpty() {
        return servers.isEmpty();
    }

    public void add(String name, boolean enabled, String url, String token) {
        servers.put(name, new Server(name, enabled, url, token, null, clientInvocation, clientCommand));
    }

    public void setVersion(String version) {
        this.version = version;
    }
    public String version() {
        return version;
    }

    public void setRegistryPrefix(String str) {
        this.registryPrefix = str;
    }

    public String registryPrefix() {
        if (!registryPrefix.endsWith("/")) {
            throw new IllegalStateException(registryPrefix);
        }
        return registryPrefix;
    }

    public Server context() throws IOException {
        for (Server server : servers.values()) {
            if (server.enabled) {
                return server;
            }
        }
        throw new IOException("context not set");
    }

    public Configuration withContext(String name) {
        Configuration result;
        Server server;

        result = new Configuration(file);
        result.setRegistryPrefix(registryPrefix);
        for (Map.Entry<String, Server> entry : servers.entrySet()) {
            server = entry.getValue();
            result.servers.put(entry.getKey(), server.withEnabled(name.equals(server.name)));
        }
        return result;
    }

    public Reference reference(String stageName) throws IOException {
        return new Reference(context().connect(file.getWorld()), stageName);
    }

    public Server serverGet(String server) {
        Server result;

        result = serverLookup(server);
        if (result == null) {
            throw new ArgumentException("unknown server: " + server);
        }
        if (!result.enabled) {
            throw new ArgumentException("server is disabled: " + server);
        }
        return result;
    }

    public Server serverLookup(String server) {
        return servers.get(server);
    }

    public Client connectContext() throws IOException {
        return context().connect(file.getWorld());
    }

    public List<Reference> list(String filter) throws IOException {
        Client client;
        List<Reference> result;

        client = connectContext();
        result = new ArrayList<>();
        result.addAll(Reference.list(client, client.list(filter)));
        return result;
    }

    public void load() throws IOException {
        JsonObject all;
        JsonArray array;
        Server server;

        servers.clear();

        all = JsonParser.parseString(file.readString()).getAsJsonObject();
        registryPrefix = all.get("registryPrefix").getAsString();
        array = all.get("servers").getAsJsonArray();
        for (JsonElement element : array) {
            server = Server.fromJson(element.getAsJsonObject(), wirelog, clientInvocation, clientCommand);
            servers.put(server.name, server);
        }
    }

    public void save(Gson gson) throws IOException {
        JsonArray array;
        JsonObject obj;

        array = new JsonArray();
        for (Server server : servers.values()) {
            array.add(server.toJson());
        }
        obj = new JsonObject();
        obj.add("registryPrefix", new JsonPrimitive(registryPrefix));
        obj.add("servers", array);
        try (Writer writer = file.newWriter()) {
            gson.toJson(obj, writer);
        }
    }

    public boolean needAuthentication() {
        for (Server server : servers.values()) {
            if (server.enabled && server.hasToken()) {
                return true;
            }
        }
        return false;
    }

    public Configuration withFile(FileNode otherFile) {
        Configuration result;

        result = new Configuration(otherFile);
        for (Server s : allServer()) {
            s.addTo(result);
        }
        result.setRegistryPrefix(registryPrefix);
        result.setVersion(version);
        return result;
    }


    public Collection<Server> allServer() {
        return Collections.unmodifiableCollection(servers.values());
    }
}
