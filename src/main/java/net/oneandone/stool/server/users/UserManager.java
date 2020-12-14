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
package net.oneandone.stool.server.users;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.oneandone.stool.server.Server;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Reader;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * Thread safe.
 * TODO: doesn't work with replicas
 * TODO: saving to a file doesn't make sense in a container ...
 */
public class UserManager {
    public static final User ANONYMOUS = new User("anonymous", "Anonymous", null);

    public static UserManager loadOpt(FileNode file) throws IOException {
        UserManager result;

        result = new UserManager(file);
        if (file.exists()) {
            result.load();
        }
        return result;
    }

    private final FileNode file;

    /** maps token to user */
    private final Map<String, Pair> tokens;

    private final Random random;

    public UserManager(FileNode file) {
        this.file = file;
        this.tokens = new HashMap<>();
        this.random = new SecureRandom();
    }

    public synchronized User byLogin(String login) throws UserNotFound {
        if (login.equals(ANONYMOUS.login)) {
            return ANONYMOUS;
        }
        for (Pair pair : tokens.values()) {
            if (login.equals(pair.user.login)) {
                return pair.user;
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

    public synchronized User authentication(String token) throws IOException {
        Pair found;

        found = tokens.get(token);
        if (found == null) {
            return null;
        } else if (found.token.created.plusDays(7).isBefore(LocalDateTime.now())) {
            Server.LOGGER.info("token expired: " + found.token + " " + found.user);
            tokens.remove(token);
            save();
            return null;
        } else {
            return found.user;
        }
    }

    public synchronized String generateToken(User user) throws IOException {
        Token token;

        remove(user.login);
        token = Token.generate(random);
        if (tokens.containsKey(token.value)) {
            throw new IllegalStateException("duplicate token: " + token);
        }
        tokens.put(token.value, new Pair(token, user));
        save();
        return token.value;
    }

    public synchronized boolean remove(String login) {
        Iterator<Map.Entry<String, Pair>> iter;
        Map.Entry<String, Pair> entry;

        iter = tokens.entrySet().iterator();
        while (iter.hasNext()) {
            entry = iter.next();
            if (login.equals(entry.getValue().user.login)) {
                iter.remove();
                return true;
            }
        }
        return false;
    }


    private synchronized void save() throws IOException {
        JsonObject json;

        json = new JsonObject();
        for (Pair pair : tokens.values()) {
            json.add(pair.token.toString(), pair.user.toJson());
        }
        file.writeString(json.toString());
    }

    private synchronized void load() throws IOException {
        JsonObject obj;
        Token token;

        tokens.clear();

        try (Reader in = file.newReader()) {
            obj = JsonParser.parseReader(in).getAsJsonObject();
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            token = Token.fromString(entry.getKey());
            tokens.put(token.value, new Pair(token, User.fromJson(entry.getValue().getAsJsonObject())));
        }
    }
}
