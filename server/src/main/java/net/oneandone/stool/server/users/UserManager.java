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
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Reader;
import java.security.SecureRandom;
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
    private final Map<String, User> tokens;

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

    public synchronized User authentication(String token) {
        return tokens.get(token);
    }

    public synchronized String generateToken(User user) {
        String token;

        remove(user.login);
        do {
            token = generateToken();
        } while (tokens.containsKey(token));
        tokens.put(token, user);
        return token;
    }

    public synchronized boolean remove(String login) {
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


    public synchronized void save() throws IOException {
        JsonObject json;

        json = new JsonObject();
        for (Map.Entry<String, User> entry : tokens.entrySet()) {
            json.add(entry.getKey(), entry.getValue().toJson());
        }
        file.writeString(json.toString());
    }

    public synchronized void load() throws IOException {
        JsonObject obj;

        tokens.clear();

        try (Reader in = file.newReader()) {
            obj = new JsonParser().parse(in).getAsJsonObject();
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            tokens.put(entry.getKey(), User.fromJson(entry.getValue().getAsJsonObject()));
        }
    }

    //--

    private String generateToken() {
        return Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong());
    }
}
