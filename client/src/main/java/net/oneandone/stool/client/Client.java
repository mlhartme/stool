package net.oneandone.stool.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.oneandone.sushi.fs.NodeInstantiationException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.HttpFilesystem;
import net.oneandone.sushi.fs.http.HttpNode;
import net.oneandone.sushi.fs.http.model.Body;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Client {
    public static Client create(World world, FileNode wireLog, String clientInvocation, String clientCommand) throws NodeInstantiationException {
        HttpNode node;

        if (wireLog != null) {
            HttpFilesystem.wireLog(wireLog.getAbsolute());
        }
        node = (HttpNode) world.validNode("http://localhost:8080/api");
        node.getRoot().addExtraHeader("X-stool-client-invocation", clientInvocation);
        node.getRoot().addExtraHeader("X-stool-client-command", clientCommand);
        return new Client(node);
    }

    private final HttpNode root;
    private final JsonParser parser;

    public Client(HttpNode root) {
        this.root = root;
        this.parser = new JsonParser();
    }

    //--

    /** @param filter null to return all stages */
    public List<String> list(String filter) throws IOException {
        HttpNode node;
        JsonArray references;
        List<String> result;

        node = node("stages");
        if (filter != null) {
            node = node.withParameter("filter", filter);
        }
        references = httpGet(node).getAsJsonArray();
        result = new ArrayList<>(references.size());
        for (JsonElement element : references) {
            result.add(element.getAsString());
        }
        return result;
    }

    //-- create, build, start, stop, remove

    public void create(String name, Map<String, String> config) throws IOException {
        HttpNode node;
        String response;

        node = node("stages/" + name);
        node = node.withParameters(config);
        response = node.post("");
        if (!response.isEmpty()) {
            throw new IOException(response);
        }
    }

    public BuildResult build(String stage, String app, FileNode war, String comment,
                             String origin, String createdBy, String createdOn, boolean noCache, int keep,
                             Map<String, String> arguments) throws Exception {
        HttpNode node;
        JsonObject obj;
        JsonElement error;
        String result;

        node = node(stage, "build");
        node = node.withParameter("app", app);
        node = node.withParameter("war", war.getAbsolute());
        node = node.withParameter("comment", comment);
        node = node.withParameter("origin", origin);
        node = node.withParameter("created-by", createdBy);
        node = node.withParameter("created-on", createdOn);
        node = node.withParameter("no-cache", noCache);
        node = node.withParameter("keep", keep);
        node = node.withParameters("arg.", arguments);
        try (InputStream src = war.newInputStream()) {
            result = node.getWorld().getSettings().string(node.post(new Body(null, null, war.size(), src, false)));
        }
        obj = parser.parse(result).getAsJsonObject();
        error = obj.get("error");
        return new BuildResult(error == null ? null : error.getAsString(), obj.get("output").getAsString());
    }

    public void start(String stage, int http, int https, Map<String, String> startEnvironment, Map<String, Integer> apps) throws IOException {
        HttpNode node;
        String response;

        node = node(stage, "start");
        node = node.withParameter("http", http);
        node = node.withParameter("https", https);
        node = node.withParameters("env.", startEnvironment);
        node = node.withParameters("app.", apps);
        response = node.post("");
        if (!response.isEmpty()) {
            throw new IOException(response);
        }
    }

    public Map<String, List<String>> awaitStartup(String stage) throws IOException {
        JsonObject response;
        Map<String, List<String>> result;

        response = httpGet(node(stage, "await-startup")).getAsJsonObject();

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

    public void stop(String stage, List<String> apps) throws IOException {
        String response;

        response = node(stage, "stop").withParameter("apps", Separator.COMMA.join(apps)).post("");
        if (!response.isEmpty()) {
            throw new IOException(response);
        }
    }

    public void remove(String stage) throws IOException {
        String response;

        response = node(stage, "remove").post("");
        if (!response.isEmpty()) {
            throw new IOException(response);
        }
    }

    //--

    public Map<String, String> status(String stage, List<String> select) throws IOException {
        HttpNode node;
        JsonObject status;
        Map<String, String> result;

        node = node(stage, "status");
        node = node.withParameter("select", Separator.COMMA.join(select));
        status = httpGet(node).getAsJsonObject();
        result = new LinkedHashMap<>();
        for (String name : status.keySet()) {
            result.put(name, status.get(name).getAsString());
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
        references = httpGet(node).getAsJsonArray();
        result = new ArrayList<>(references.size());
        for (JsonElement element : references) {
            result.add(element.getAsString());
        }
        return result;
    }

    public String quota() throws IOException {
        String result;

        result = httpGet(node("quota")).getAsString();
        return result.isEmpty() ? null : result;
    }

    public int memUnreserved() throws IOException {
        String result;

        result = httpGet(node("memUnreserved")).getAsString();
        return Integer.parseInt(result);
    }

    public List<String> apps(String stage) throws IOException {
        return array(httpGet(node(stage, "apps")).getAsJsonArray());
    }

    //-- validate

    public List<String> validate(String stageClause, boolean email, boolean repair) throws IOException {
        HttpNode node;
        String response;

        node = node("validate");
        node = node.withParameter("stageClause", stageClause);
        node = node.withParameter("email", email);
        node = node.withParameter("repair", repair);
        response = node.post("");
        return array(parser.parse(response).getAsJsonArray());
    }

    //-- config command

    public Map<String, String> getProperties(String stage) throws Exception {
        Map<String, String> result;
        JsonObject properties;

        properties = httpGet(node(stage, "properties")).getAsJsonObject();
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

        response = parser.parse(node.post("")).getAsJsonObject();

        result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : response.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    //-- app info

    public List<String> appInfo(String stage, String app) throws Exception {
        return array(httpGet(node(stage, "appInfo").withParameter("app", app)).getAsJsonArray());
    }

    //--

    private HttpNode node(String stage, String cmd) {
        return node("stages/" + stage + "/" + cmd);
    }

    private HttpNode node(String path) {
        return root.join(path);
    }

    private JsonElement httpGet(HttpNode node) throws IOException {
        String response;

        response = node.readString();
        //System.out.println("path: " + path);
        //System.out.println("response: " + response);
        return parser.parse(response);
    }
}