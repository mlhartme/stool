package net.oneandone.stool.util;

import net.oneandone.inline.ArgumentException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Misc {
    public static Map<String, String> assignments(List<String> args) {
        Map<String, String> result;
        int idx;
        String key;
        String value;

        result = new LinkedHashMap<>();
        for (String arg : args) {
            idx = arg.indexOf('=');
            if (idx == -1) {
                throw new ArgumentException("key=values expected, got " + arg);
            }
            key = arg.substring(0, idx);
            value = arg.substring(idx + 1);
            if (result.put(key, value) != null) {
                throw new ArgumentException("duplicate key: " + key);
            }
        }
        return result;
    }

    private Misc() {
    }
}
