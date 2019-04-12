package net.oneandone.stool.server.util;

import net.oneandone.stool.server.users.User;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TokenManager {
    /** maps user to token */
    private Map<User, String> tokens;

    private final Random random;

    public TokenManager() {
        this.tokens = new HashMap<>();
        this.random = new Random();
    }

    public String create(User user) {
        String token;

        token = generateToken();
        tokens.put(user, token);
        return token;
    }

    public User authentication(String token) {
        for (Map.Entry<User, String> entry : tokens.entrySet()) {
            if (token.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String generateToken() {
        return Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong());
    }
}
