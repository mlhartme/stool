package net.oneandone.stool.server.util;

import net.oneandone.stool.server.users.User;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
        Properties p;
        User user;

        p = new Properties();
        for (Map.Entry<String, User> entry : tokens.entrySet()) {
            user = entry.getValue();
            p.put(entry.getKey(), user.login + "," + user.name + "," + user.email);
        }
        file.writeProperties(p);
    }

    public void load() throws IOException {
        tokens.clear();
        for (Map.Entry<Object, Object> entry : file.readProperties().entrySet()) {
            tokens.put((String) entry.getKey(), user((String) entry.getValue()));
        }
    }

    private static User user(String str) throws IOException {
        List<String> lst;

        lst = Separator.COMMA.split(str);
        if (lst.size() != 3) {
            throw new IOException("not a user: " + str);
        }
        return new User(lst.get(0), lst.get(1), lst.get(2));
    }
}
