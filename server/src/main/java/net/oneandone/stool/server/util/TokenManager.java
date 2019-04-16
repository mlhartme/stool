package net.oneandone.stool.server.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.oneandone.stool.server.users.User;
import net.oneandone.stool.server.users.UserNotFound;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

public class TokenManager {
    public static TokenManager loadOpt(FileNode file) throws IOException {
        TokenManager result;

        result = new TokenManager(file);
        if (file.exists()) {
            result.load();
        }
        return result;
    }

    private final FileNode file;

    /** maps token to user */
    private final Map<String, User> tokens;

    private final Random random;

    public TokenManager(FileNode file) {
        this.file = file;
        this.tokens = new HashMap<>();
        this.random = new Random();
    }

    public User byLogin(String login) throws UserNotFound {
        for (User user : tokens.values()) {
            if (login.equals(user.login)) {
                return user;
            }
        }
        throw new UserNotFound(login);
    }

    public String checkedByLogin(String login) {
        try {
            return byLogin(login).toStatus();
        } catch (UserNotFound e) {
            return "[error: " + e.getMessage() + "]";
        }
    }

    public User authentication(String token) {
        return tokens.get(token);
    }

    public String create(User user) {
        String token;

        remove(user.login);
        do {
            token = generateToken();
        } while (tokens.containsKey(token));
        tokens.put(token, user);
        return token;
    }

    public boolean remove(String login) {
        Iterator<Map.Entry<String, User>> iter;
        Map.Entry<String, User> entry;

        iter = tokens.entrySet().iterator();
        while (iter.hasNext()) {
            entry = iter.next();
            if (login.equals(entry.getValue().login)) {
                iter.remove();
                return true;
            }
        }
        return false;
    }

    private String generateToken() {
        return Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong());
    }


    public void save() throws IOException {
        JsonObject json;

        json = new JsonObject();
        for (Map.Entry<String, User> entry : tokens.entrySet()) {
            json.add(entry.getKey(), entry.getValue().toJson());
        }
        file.writeString(json.toString());
    }

    public void load() throws IOException {
        JsonObject obj;

        tokens.clear();

        try (Reader in = file.newReader()) {
            obj = new JsonParser().parse(in).getAsJsonObject();
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            tokens.put(entry.getKey(), User.fromJson(entry.getValue().getAsJsonObject()));
        }
    }
}
