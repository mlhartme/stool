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

    public void setRegistryPrefix(String str) {
        this.registryPrefix = str;
    }

    public String registryPrefix() {
        if (!registryPrefix.endsWith("/")) {
            throw new IllegalStateException(registryPrefix);
        }
        return registryPrefix;
    }

    public Reference serverReference(String str) throws IOException {
        int idx;
        String server;

        idx = str.indexOf('@');
        if (idx == -1) {
            throw new IllegalArgumentException(str);
        }
        server = str.substring(idx + 1);
        return new Reference(serverGet(server).connect(file.getWorld()), str.substring(0, idx));
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

    /**
     * @param filter may be null
     * @return may be null
     */
    public String serverFilter(String filter) {
        int idx;

        if (filter == null) {
            return "";
        } else {
            idx = filter.lastIndexOf('@');
            return idx == -1 ? "" : filter.substring(idx + 1);
        }
    }

    /**
     * @param filter may be null
     * @return never null
     */
    public String clientFilter(String filter) {
        int idx;

        if (filter == null) {
            return null;
        } else {
            idx = filter.lastIndexOf('@');
            return idx == -1 ? filter : filter.substring(0, idx);
        }
    }

    /** @param serverFilter never null */
    public List<Client> connectMatching(String serverFilter) throws IOException {
        List<Client> result;

        result = new ArrayList<>();
        for (Server server : servers.values()) {
            if (server.enabled) {
                if (serverFilter.isEmpty() || server.name.toLowerCase().equals(serverFilter.toLowerCase())) {
                    result.add(server.connect(file.getWorld()));
                }
            }
        }
        return result;
    }

    public List<Reference> list(String filter) throws IOException {
        List<Reference> result;
        String clientFilter;
        String serverFilter;

        serverFilter = serverFilter(filter);
        clientFilter = clientFilter(filter);
        result = new ArrayList<>();
        for (Client client : connectMatching(serverFilter)) {
            result.addAll(Reference.list(client, client.list(clientFilter)));
        }
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

    public Configuration newEnabled() {
        Configuration result;

        result = new Configuration(file);
        for (Map.Entry<String, Server> entry : servers.entrySet()) {
            result.servers.put(entry.getKey(), entry.getValue().withEnabled(true));
        }
        return result;
    }

    public Collection<Server> allServer() {
        return Collections.unmodifiableCollection(servers.values());
    }

    public Collection<Server> enabledServer() {
        List<Server> lst;

        lst = new ArrayList<>();
        for (Server server : servers.values()) {
            if (server.enabled) {
                lst.add(server);
            }
        }
        return Collections.unmodifiableList(lst);
    }
}
