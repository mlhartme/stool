package net.oneandone.stool.server.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TokenManager {
    /** maps user to token */
    private Map<String, String> tokens;

    private final Random random;

    public TokenManager() {
        this.tokens = new HashMap<>();
        this.random = new Random();
    }

    public String create(String user) {
        String token;

        token = generateToken();
        tokens.put(user, token);
        return token;
    }

    public String authentication(String token) {
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            if (token.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String generateToken() {
        return Integer.toString(random.nextInt());
    }
}
