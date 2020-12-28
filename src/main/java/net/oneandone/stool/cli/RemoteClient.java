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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Iterator;
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

    private final ObjectMapper json;
    private final HttpNode root;

    public RemoteClient(String context, HttpNode root) {
        super(context);
        this.json = new ObjectMapper();
        this.root = root;
    }

    public String getServer() {
        return root.getRoot().getHostname();
    }

    public String auth() throws IOException {
        HttpNode node;

        node = node("auth");
        return postJson(node, "").asText();
    }

    /**
     * @param filter null to return all stages
     * @return stage -&gt; (field -&gt; value)
     */
    @Override
    public Map<String, Map<String, JsonNode>> list(String filter, List<String> select) throws IOException {
        HttpNode node;
        ObjectNode response;
        Map<String, Map<String, JsonNode>> result;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;

        node = node("stages");
        if (filter != null) {
            node = node.withParameter("filter", filter);
        }
        node = node.withParameter("select", select.isEmpty() ? "*" : Separator.COMMA.join(select));
        response = (ObjectNode) getJson(node);
        result = new LinkedHashMap<>();
        iter = response.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            result.put(entry.getKey(), map((ObjectNode) entry.getValue()));
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

        node = node("stages/" + stage);
        node = node.withParameter("image", image);
        node = node.withParameters("value.", values);
        return stringMap((ObjectNode) postJson(node, ""));
    }

    /** @return tag actually started */
    @Override
    public String publish(Caller callerTodo, String stage, String imageOpt, Map<String, String> values) throws IOException {
        HttpNode node;
        JsonNode started;

        node = node(stage, "publish");
        if (imageOpt != null) {
            node = node.withParameters("image", imageOpt);
        }
        node = node.withParameters("value.", values);
        started = postJson(node, "");
        return started.asText();
    }

    @Override
    public Map<String, String> awaitAvailable(String stage) throws IOException {
        ObjectNode response;

        response = (ObjectNode) getJson(node(stage, "await-available"));
        return stringMap(response);
    }

    /** @return json with pod and token fields */
    @Override
    public PodConfig podToken(String stage, int timeout) throws IOException {
        HttpNode node;
        ObjectNode pod;

        node = node(stage, "pod-token").withParameter("timeout", timeout);
        pod = (ObjectNode) getJson(node);
        return new PodConfig(str(pod, "server"), str(pod, "namespace"),
                new String(Base64.getDecoder().decode(str(pod, "token")), Charset.forName("US-ASCII")),
                str(pod, "pod"));
    }

    private static String str(ObjectNode obj, String field) {
        JsonNode element;

        element = obj.get(field);
        if (element == null) {
            throw new IllegalStateException(obj + ": field not found: " + field);
        }
        return element.asText();
    }

    @Override
    public void delete(String stage) throws IOException {
        postEmpty(node(stage, "delete"), "");
    }

    //--

    @Override
    public List<String> history(String stage) throws IOException {
        HttpNode node;

        node = node(stage, "history");
        return array((ArrayNode) getJson(node));
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
        return array((ArrayNode) postJson(node, ""));
    }

    @Override
    public Map<String, String> getValues(String stage) throws IOException {
        return stringMap((ObjectNode) getJson(node(stage, "values")));
    }

    @Override
    public Map<String, String> setValues(Caller callerTodo, String stage, Map<String, String> values) throws IOException {
        HttpNode node;

        node = node(stage, "set-values").withParameters(values);
        return stringMap((ObjectNode) postJson(node, ""));
    }

    //-- images

    @Override
    public List<String> images(String stage) throws Exception {
        return array((ArrayNode) getJson(node(stage, "images")));
    }


    //--

    private HttpNode node(String stage, String cmd) {
        return node("stages/" + stage + "/" + cmd);
    }

    private HttpNode node(String path) {
        return root.join(path);
    }


    //-- http methods with exception handling

    private JsonNode getJson(HttpNode node) throws IOException {
        return stream(node, null, "GET", 200);
    }

    private JsonNode postJson(HttpNode node, String body) throws IOException {
        byte[] bytes;

        bytes = node.getWorld().getSettings().bytes(body);
        return postJson(node, new Body(null, null, (long) bytes.length, new ByteArrayInputStream(bytes), false));
    }

    private JsonNode postJson(HttpNode node, Body body) throws IOException {
        return stream(node, body, "POST", 200, 201);
    }

    private JsonNode stream(HttpNode node, Body body, String method, int... success) throws IOException {
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
                            return json.readTree(reader(src));
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
        JsonNode e;

        e = postJson(node, body);
        if (!e.isNull()) {
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

    private static List<String> array(ArrayNode json) {
        List<String> result;
        Iterator<JsonNode> iter;

        result = new ArrayList<>(json.size());
        iter = json.elements();
        while (iter.hasNext()) {
            result.add(iter.next().asText());
        }
        return result;
    }

    public static Map<String, String> stringMap(ObjectNode obj) {
        Map<String, String> result;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;

        result = new LinkedHashMap<>();
        iter = obj.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            result.put(entry.getKey(), entry.getValue().asText());
        }
        return result;
    }

    public static Map<String, JsonNode> map(ObjectNode infos) {
        Map<String, JsonNode> result;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;

        result = new LinkedHashMap<>();
        iter = infos.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
