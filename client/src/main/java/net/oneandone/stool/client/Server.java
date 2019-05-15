package net.oneandone.stool.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class Server {
    public static Server fromJson(JsonObject obj, FileNode wirelog, String clientInvocation, String clientCommand) {
        JsonElement token;

        token = obj.get("token");
        return new Server(obj.get("name").getAsString(), obj.get("url").getAsString(), token == null ? null : token.getAsString(),
                wirelog, clientInvocation, clientCommand);
    }

    public final String name;
    public final String url;

    /** null to work anonymously */
    private String token;

    private volatile FileNode wirelog;
    private volatile String clientInvocation;
    private volatile String clientCommand;

    public Server(String name, String url, String token, FileNode wirelog, String clientInvocation, String clientCommand) {
        this.name = name;
        this.url = url;
        this.token = token;

        this.wirelog = wirelog;
        this.clientInvocation = clientInvocation;
        this.clientCommand = clientCommand;
    }

    /** looks weired, but this method allows we to keep the token field private */
    public void addTo(ServerManager dest) {
        dest.add(name, url, token);
    }

    public boolean hasToken() {
        return token != null;
    }
    public void auth(World world, String username, String password) throws IOException {
        Client client;

        client = Client.basicAuth(world, name, url, wirelog, clientInvocation, clientCommand, username, password);
        this.token = client.auth();
    }

    public Client connect(World world) throws IOException {
        return Client.token(world, name, url, wirelog, clientInvocation, clientCommand, token);
    }

    public JsonObject toJson() {
        JsonObject result;

        result = new JsonObject();
        result.add("name", new JsonPrimitive(name));
        result.add("url", new JsonPrimitive(url));
        if (token != null) {
            result.add("token", new JsonPrimitive(token));
        }
        return result;
    }

}
