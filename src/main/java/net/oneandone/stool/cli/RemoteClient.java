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
package net.oneandone.stool.cli;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.oneandone.inline.ArgumentException;
import net.oneandone.sushi.fs.FileNotFoundException;
import net.oneandone.sushi.fs.NodeInstantiationException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.HttpFilesystem;
import net.oneandone.sushi.fs.http.HttpNode;
import net.oneandone.sushi.fs.http.model.Body;
import net.oneandone.sushi.fs.http.model.ProtocolException;
import net.oneandone.sushi.fs.http.model.Request;
import net.oneandone.sushi.util.Separator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.nio.charset.Charset;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RemoteClient extends Client {
    /** @param token null to work anonymously */
    public static RemoteClient token(World world, String context, String url, FileNode wireLog, String clientInvocation, String clientCommand,
                                     String token) throws NodeInstantiationException {
        return doCreate(world, context, url, wireLog, clientInvocation, clientCommand, token, null, null);

    }

    public static RemoteClient basicAuth(World world, String context, String url, FileNode wireLog, String clientInvocation, String clientCommand,
                                         String username, String password) throws NodeInstantiationException {
        return doCreate(world, context, url, wireLog, clientInvocation, clientCommand, null, username, password);

    }

    private static RemoteClient doCreate(World world, String context, String url, FileNode wireLog, String clientInvocation, String clientCommand,
                                         String token, String username, String password) throws NodeInstantiationException {
        HttpNode node;

        if (wireLog != null) {
            HttpFilesystem.wireLog(wireLog.getAbsolute());
        }
        node = (HttpNode) world.validNode(url);
        node.getRoot().addExtraHeader("X-stool-client-invocation", clientInvocation);
        node.getRoot().addExtraHeader("X-stool-client-command", clientCommand);
        if (token != null) {
            node.getRoot().addExtraHeader("X-authentication", token);
        }
        if (username != null) {
            node.getRoot().setCredentials(username, password);
        }
        return new RemoteClient(context, node);
    }

    private final HttpNode root;
    private final JsonParser parser;

    public RemoteClient(String context, HttpNode root) {
        super(context);
        this.root = root;
        this.parser = new JsonParser();
    }

    public String getServer() {
        return root.getRoot().getHostname();
    }

    public String auth() throws IOException {
        HttpNode node;

        node = node("auth");
        return postJson(node, "").getAsString();
    }

    /**
     * @param filter null to return all stages
     * @return stage -&gt; (field -&gt; value)
     */
    @Override
    public Map<String, Map<String, JsonElement>> list(String filter, List<String> select) throws IOException {
        HttpNode node;
        JsonObject response;
        Map<String, Map<String, JsonElement>> result;

        node = node("stages");
        if (filter != null) {
            node = node.withParameter("filter", filter);
        }
        node = node.withParameter("select", select.isEmpty() ? "*" : Separator.COMMA.join(select));
        response = getJson(node).getAsJsonObject();
        result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : response.entrySet()) {
            result.put(entry.getKey(), map(entry.getValue().getAsJsonObject()));
        }
        return result;
    }

    //-- create, start, stop, remove

    /**
     * @return image actually started
     * @throws FileAlreadyExistsException if the stage already exists */
    @Override
    public Map<String, String> create(Caller callerTodo, String stage, String image, Map<String, String> values) throws IOException {
        HttpNode node;
        JsonObject response;

        node = node("stages/" + stage);
        node = node.withParameter("image", image);
        node = node.withParameters("value.", values);
        response = postJson(node, "").getAsJsonObject();
        return stringMap(response.getAsJsonObject());
    }

    /** @return tag actually started */
    @Override
    public String publish(Caller callerTodo, String stage, String imageOpt, Map<String, String> values) throws IOException {
        HttpNode node;
        JsonElement started;

        node = node(stage, "publish");
        if (imageOpt != null) {
            node = node.withParameters("image", imageOpt);
        }
        node = node.withParameters("value.", values);
        started = postJson(node, "");
        return started.getAsString();
    }

    @Override
    public Map<String, String> awaitAvailable(String stage) throws IOException {
        JsonObject response;

        response = getJson(node(stage, "await-available")).getAsJsonObject();
        return stringMap(response.getAsJsonObject());
    }

    /** @return json with pod and token fields */
    @Override
    public PodConfig podToken(String stage, int timeout) throws IOException {
        HttpNode node;
        JsonObject json;

        node = node(stage, "pod-token").withParameter("timeout", timeout);
        json = getJson(node).getAsJsonObject();
        return new PodConfig(str(json, "server"), str(json, "namespace"),
                new String(Base64.getDecoder().decode(str(json, "token")), Charset.forName("US-ASCII")),
                str(json, "pod"));
    }

    private static String str(JsonObject obj, String field) {
        JsonElement element;

        element = obj.get(field);
        if (element == null) {
            throw new IllegalStateException(obj + ": field not found: " + field);
        }
        return element.getAsString();
    }

    @Override
    public void delete(String stage) throws IOException {
        postEmpty(node(stage, "delete"), "");
    }

    //--

    @Override
    public List<String> history(String stage) throws IOException {
        HttpNode node;
        JsonArray references;
        List<String> result;

        node = node(stage, "history");
        references = getJson(node).getAsJsonArray();
        result = new ArrayList<>(references.size());
        for (JsonElement element : references) {
            result.add(element.getAsString());
        }
        return result;
    }

    @Override
    public String version() throws IOException {
        return node("version").readString();
    }

    @Override
    public List<String> validate(String stage, boolean email, boolean repair) throws IOException {
        HttpNode node;

        node = node(stage, "validate");
        node = node.withParameter("email", email);
        node = node.withParameter("repair", repair);
        return array(postJson(node, "").getAsJsonArray());
    }

    @Override
    public Map<String, String> getValues(String stage) throws IOException {
        Map<String, String> result;
        JsonObject values;

        values = getJson(node(stage, "values")).getAsJsonObject();
        result = new LinkedHashMap<>();
        for (String value : values.keySet()) {
            result.put(value, values.get(value).getAsString());
        }
        return result;
    }

    @Override
    public Map<String, String> setValues(Caller callerTodo, String stage, Map<String, String> values) throws IOException {
        HttpNode node;
        JsonObject response;
        Map<String, String> result;

        node = node(stage, "set-values");
        node = node.withParameters(values);

        response = postJson(node, "").getAsJsonObject();
        result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : response.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    //-- images

    @Override
    public List<String> images(String stage) throws Exception {
        return array(getJson(node(stage, "images")).getAsJsonArray());
    }


    //--

    private HttpNode node(String stage, String cmd) {
        return node("stages/" + stage + "/" + cmd);
    }

    private HttpNode node(String path) {
        return root.join(path);
    }


    //-- http methods with exception handling

    private JsonElement getJson(HttpNode node) throws IOException {
        return stream(node, null, "GET", 200);
    }

    private JsonElement postJson(HttpNode node, String body) throws IOException {
        byte[] bytes;

        bytes = node.getWorld().getSettings().bytes(body);
        return postJson(node, new Body(null, null, (long) bytes.length, new ByteArrayInputStream(bytes), false));
    }

    private JsonElement postJson(HttpNode node, Body body) throws IOException {
        return stream(node, body, "POST", 200, 201);
    }

    private JsonElement stream(HttpNode node, Body body, String method, int... success) throws IOException {
        int code;

        try (Request.ResponseStream src = Request.streamResponse(node, method, body, null)) {
            code = src.getStatusLine().code;
            switch (code) {
                case 400:
                    throw new ArgumentException(string(src));
                case 401:
                    throw new AuthenticationException(node.getUri());
                case 404:
                    throw new FileNotFoundException(node, "not found");
                case 409:
                    throw new FileAlreadyExistsException(node.getPath(), code + "", src.getStatusLine().toString());
                default:
                    for (int c : success) {
                        if (code == c) {
                            return parser.parse(reader(src));
                        }
                    }
                    throw new IOException(node.getUri() + " returned http response code " + src.getStatusLine().code + "\n" + string(src));
            }
        } catch (ProtocolException e) {
            throw new IOException(root.toString() + ": " + e.getMessage());
        } catch (ConnectException e) {
            if (e.getMessage().toLowerCase().contains("connection refused")) {
                throw new IOException(node.getRoot() + ": " + e.getMessage(), e);
            } else {
                throw e;
            }
        }
    }

    private String string(InputStream src) throws IOException {
        return root.getWorld().getBuffer().readString(src, root.getWorld().getSettings().encoding);
    }

    private void postEmpty(HttpNode node, String body) throws IOException {
        JsonElement e;

        e = postJson(node, body);
        if (!e.isJsonNull()) {
            throw new IOException("unexpected response: " + e);
        }
    }

    private InputStreamReader reader(InputStream src) {
        try {
            return new InputStreamReader(src, root.getWorld().getSettings().encoding);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    //-- json helper

    private static List<String> array(JsonArray json) {
        List<String> result;

        result = new ArrayList<>(json.size());
        for (JsonElement element : json) {
            result.add(element.getAsString());
        }
        return result;
    }

    private static Map<String, String> stringMap(JsonObject json) {
        Map<String, String> result;

        result = new LinkedHashMap<>(json.size());
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    private static Map<String, JsonElement> map(JsonObject infos) {
        Map<String, JsonElement> result;

        result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : infos.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }
}