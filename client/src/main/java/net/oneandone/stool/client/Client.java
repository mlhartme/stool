package net.oneandone.stool.client;

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
import net.oneandone.sushi.fs.http.model.Request;
import net.oneandone.sushi.util.Separator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Client {
    /** @param token null to work anonymously */
    public static Client token(World world, String name, String url, FileNode wireLog, String clientInvocation, String clientCommand,
                                   String token) throws NodeInstantiationException {
        return doCreate(world, name, url, wireLog, clientInvocation, clientCommand, token, null, null);

    }

    public static Client basicAuth(World world, String name, String url, FileNode wireLog, String clientInvocation, String clientCommand,
                                   String username, String password) throws NodeInstantiationException {
        return doCreate(world, name, url, wireLog, clientInvocation, clientCommand, null, username, password);

    }

    private static Client doCreate(World world, String name, String url, FileNode wireLog, String clientInvocation, String clientCommand,
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
        return new Client(name, node);
    }

    private final String name;
    private final HttpNode root;
    private final JsonParser parser;

    public Client(String name, HttpNode root) {
        this.name = name;
        this.root = root;
        this.parser = new JsonParser();
    }

    public String getName() {
        return name;
    }

    //--

    public String auth() throws IOException {
        HttpNode node;

        node = node("auth");
        return postJson(node, "").getAsString();
    }

    /** @param filter null to return all stages */
    public List<String> list(String filter) throws IOException {
        HttpNode node;
        JsonArray references;
        List<String> result;

        node = node("stages");
        if (filter != null) {
            node = node.withParameter("filter", filter);
        }
        references = getJson(node).getAsJsonArray();
        result = new ArrayList<>(references.size());
        for (JsonElement element : references) {
            result.add(element.getAsString());
        }
        return result;
    }

    //-- create, build, start, stop, remove

    public void create(String name, Map<String, String> config) throws IOException {
        HttpNode node;

        node = node("stages/" + name);
        node = node.withParameters(config);
        postEmpty(node, "");
    }

    public BuildResult build(String stage, FileNode war, String comment,
                             String origin, String createdBy, String createdOn, boolean noCache, int keep,
                             Map<String, String> arguments) throws Exception {
        HttpNode node;
        JsonObject obj;
        JsonElement error;

        node = node(stage, "build");
        node = node.withParameter("war", war.getAbsolute());
        node = node.withParameter("comment", comment);
        node = node.withParameter("origin", origin);
        node = node.withParameter("created-by", createdBy);
        node = node.withParameter("created-on", createdOn);
        node = node.withParameter("no-cache", noCache);
        node = node.withParameter("keep", keep);
        node = node.withParameters("arg.", arguments);
        try (InputStream src = war.newInputStream()) {
            obj = postJson(node, new Body(null, null, war.size(), src, false)).getAsJsonObject();
        }
        error = obj.get("error");
        return new BuildResult(obj.get("output").getAsString(), error == null ? null : error.getAsString(), obj.get("app").getAsString(), obj.get("tag").getAsString());
    }

    public List<String> start(String stage, int http, int https, Map<String, String> startEnvironment, Map<String, Integer> apps) throws IOException {
        HttpNode node;
        List<String> started;

        node = node(stage, "start");
        node = node.withParameter("http", http);
        node = node.withParameter("https", https);
        node = node.withParameters("env.", startEnvironment);
        node = node.withParameters("app.", apps);
        started = array(postJson(node, "").getAsJsonArray());
        if (started.isEmpty()) {
            throw new IOException("stage is already started");
        }
        return started;
    }

    public Map<String, List<String>> awaitStartup(String stage) throws IOException {
        JsonObject response;
        Map<String, List<String>> result;

        response = getJson(node(stage, "await-startup")).getAsJsonObject();

        result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : response.entrySet()) {
            result.put(entry.getKey(), array(entry.getValue().getAsJsonArray()));
        }
        return result;
    }

    private static List<String> array(JsonArray json) {
        List<String> result;

        result = new ArrayList<>(json.size());
        for (JsonElement element : json) {
            result.add(element.getAsString());
        }
        return result;
    }

    public List<String> stop(String stage, List<String> apps) throws IOException {
        List<String> stopped;
        HttpNode node;

        node = node(stage, "stop").withParameter("apps", Separator.COMMA.join(apps));
        stopped = array(postJson(node, "").getAsJsonArray());
        if (stopped.isEmpty()) {
            throw new IOException("stage is already stopped");
        }
        return stopped;
    }

    public void remove(String stage) throws IOException {
        postEmpty(node(stage, "remove"), "");
    }

    //--

    public Map<String, String> status(String stage, List<String> select) throws IOException {
        HttpNode node;
        JsonObject status;
        LinkedHashMap<String, String> result;

        node = node(stage, "status");
        node = node.withParameter("select", Separator.COMMA.join(select));
        status = getJson(node).getAsJsonObject();
        result = new LinkedHashMap<>();
        for (String name : status.keySet()) {
            result.put(name, status.get(name).getAsString());
        }
        if (result.containsKey("name")) {
            result.replace("name", new Reference(this, stage).toString());
        }
        return result;
    }

    public List<String> history(String stage, boolean details, int max) throws IOException {
        HttpNode node;
        JsonArray references;
        List<String> result;

        node = node(stage,"history");
        node = node.withParameter("details", details);
        node = node.withParameter("max", max);
        references = getJson(node).getAsJsonArray();
        result = new ArrayList<>(references.size());
        for (JsonElement element : references) {
            result.add(element.getAsString());
        }
        return result;
    }

    public String quota() throws IOException {
        String result;

        result = getJson(node("quota")).getAsString();
        return result.isEmpty() ? null : result;
    }

    //-- validate

    public List<String> validate(String stage, boolean email, boolean repair) throws IOException {
        HttpNode node;

        node = node(stage,"validate");
        node = node.withParameter("email", email);
        node = node.withParameter("repair", repair);
        return array(postJson(node,"").getAsJsonArray());
    }

    //-- config command

    public Map<String, String> getProperties(String stage) throws Exception {
        Map<String, String> result;
        JsonObject properties;

        properties = getJson(node(stage, "properties")).getAsJsonObject();
        result = new LinkedHashMap<>();
        for (String name : properties.keySet()) {
            result.put(name, properties.get(name).getAsString());
        }
        return result;
    }

    public Map<String, String> setProperties(String stage, Map<String, String> arguments) throws IOException {
        HttpNode node;
        JsonObject response;
        Map<String, String> result;

        node = node(stage, "set-properties");
        node = node.withParameters(arguments);

        response = postJson(node, "").getAsJsonObject();
        result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : response.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    //-- app info

    public List<String> appInfo(String stage, String app) throws Exception {
        return array(getJson(node(stage, "appInfo").withParameter("app", app)).getAsJsonArray());
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
        return postJson(node, new Body(null, null, (long)bytes.length, new ByteArrayInputStream(bytes), false));
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
                    throw new IOException("unauthenticated: " + node.getUri());
                case 404:
                    throw new FileNotFoundException(node, "not found");
                default:
                    for (int c : success) {
                        if (code == c) {
                            return parser.parse(reader(src));
                        }
                    }
                    throw new IOException(node.getUri() + " returned http response code " + src.getStatusLine().code + "\n" + string(src));
            }
        } catch (ConnectException e) {
            if (e.getMessage().toLowerCase().contains("connection refuse")) {
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
}
