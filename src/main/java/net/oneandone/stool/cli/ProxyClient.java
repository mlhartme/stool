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
import net.oneandone.stool.classes.ClassRef;
import net.oneandone.stool.util.Json;
import net.oneandone.stool.util.Diff;
import net.oneandone.stool.util.Pair;
import net.oneandone.sushi.fs.FileNotFoundException;
import net.oneandone.sushi.fs.NodeInstantiationException;
import net.oneandone.sushi.fs.World;
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
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProxyClient extends Client {
    /** @param token null to work anonymously */
    public static ProxyClient token(World world, ObjectMapper json, String context, String url, Caller caller,
                                    String token) throws NodeInstantiationException {
        return doCreate(world, json, context, url, caller, token, null, null);

    }

    public static ProxyClient basicAuth(World world, ObjectMapper json, String context, String url, Caller caller,
                                        String username, String password) throws NodeInstantiationException {
        return doCreate(world, json, context, url, caller, null, username, password);

    }

    private static ProxyClient doCreate(World world, ObjectMapper json, String context, String url, Caller caller,
                                        String token, String username, String password) throws NodeInstantiationException {
        HttpNode node;

        if (caller.wirelog != null) {
            HttpFilesystem.wireLog(caller.wirelog.getAbsolute());
        }
        node = (HttpNode) world.validNode(url);
        node.getRoot().addExtraHeader("X-stool-client-invocation", caller.invocation);
        node.getRoot().addExtraHeader("X-stool-client-command", caller.command);
        if (token != null) {
            node.getRoot().addExtraHeader("X-authentication", token);
        }
        if (username != null) {
            node.getRoot().setCredentials(username, password);
        }
        return new ProxyClient(json, context, node, caller);
    }

    private final ObjectMapper json;
    private final HttpNode root;

    public ProxyClient(ObjectMapper json, String context, HttpNode root, Caller caller) {
        super(context, caller);
        this.json = json;
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
    public Map<String, Map<String, JsonNode>> list(String filter, List<String> select, boolean hidden) throws IOException {
        HttpNode node;
        ObjectNode response;
        Map<String, Map<String, JsonNode>> result;
        Iterator<Map.Entry<String, JsonNode>> iter;
        Map.Entry<String, JsonNode> entry;

        node = node("stages");
        if (filter != null) {
            node = node.withParameter("filter", filter);
        }
        if (hidden) {
            node = node.withParameter("hidden", hidden);
        }
        node = node.withParameter("select", select.isEmpty() ? "*" : Separator.COMMA.join(select));
        response = (ObjectNode) getJson(node);
        result = new LinkedHashMap<>();
        iter = response.fields();
        while (iter.hasNext()) {
            entry = iter.next();
            result.put(entry.getKey(), Json.map((ObjectNode) entry.getValue()));
        }
        return result;
    }

    //--

    /**
     * @return url map
     * @throws FileAlreadyExistsException if the stage already exists */
    @Override
    public Map<String, String> create(String stageName, ClassRef classRef, Map<String, String> values) throws IOException {
        HttpNode node;

        node = node("stages/" + stageName);
        node = node.withParameter("classref", classRef.serialize());
        node = node.withParameters("value.", values);
        return Json.stringMap((ObjectNode) postJson(node, ""));
    }

    @Override
    public Diff publish(String stage, boolean dryrun, String allow, ClassRef classRefOpt, Map<String, String> values) throws IOException {
        HttpNode node;

        node = node(stage, "publish");
        if (classRefOpt != null) {
            node = node.withParameter("classref", classRefOpt.serialize());
        }
        if (dryrun) {
            node = node.withParameter("dryrun", dryrun);
        }
        if (allow != null) {
            node = node.withParameter("allow", allow);
        }
        node = node.withParameters("value.", values);
        return Diff.fromList(Json.list((ArrayNode) postJson(node, "")));
    }

    @Override
    public Map<String, String> awaitAvailable(String stage) throws IOException {
        return Json.stringMap((ObjectNode) getJson(node(stage, "await-available")));
    }

    /** @return json with pod and token fields */
    @Override
    public PodConfig podToken(String stage, int timeout) throws IOException {
        HttpNode node;
        ObjectNode pod;

        node = node(stage, "pod-token").withParameter("timeout", timeout);
        pod = (ObjectNode) getJson(node);
        return new PodConfig(Json.string(pod, "server"), Json.string(pod, "namespace"),
                new String(Base64.getDecoder().decode(Json.string(pod, "token")), Charset.forName("US-ASCII")),
                Json.string(pod, "pod"));
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
        return Json.list((ArrayNode) getJson(node));
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
        return Json.list((ArrayNode) postJson(node, ""));
    }

    @Override
    public Map<String, Pair> getValues(String stage) throws IOException {
        return Json.stringPairMap((ObjectNode) getJson(node(stage, "values")));
    }

    @Override
    public Map<String, String> setValues(String stage, Map<String, String> values) throws IOException {
        HttpNode node;

        node = node(stage, "set-values").withParameters(values);
        return Json.stringMap((ObjectNode) postJson(node, ""));
    }

    //-- images

    @Override
    public List<String> images(String image) throws Exception {
        return Json.list((ArrayNode) getJson(node("images", image)));
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
                case 420:
                    throw new IOException(string(src));
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
        if (!e.isMissingNode()) {
            throw new IOException("unexpected response: " + e + " " + e.getClass());
        }
    }

    private InputStreamReader reader(InputStream src) {
        try {
            return new InputStreamReader(src, root.getWorld().getSettings().encoding);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
