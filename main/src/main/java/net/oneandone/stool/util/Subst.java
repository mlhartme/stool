package net.oneandone.stool.util;

import java.util.Map;

/** Substitute one-character variables. Sushi Substitution needs a none-empty end marker. */
public class Subst {
    public static String subst(String str, Map<Character, String> map) {
        int prev;
        int pos;
        StringBuilder builder;
        String s;
        char c;

        builder = new StringBuilder();
        prev = 0;
        while (true) {
            pos = str.indexOf('%', prev);
            if (pos == -1 || pos == str.length() - 1) {
                builder.append(str.substring(prev));
                return builder.toString();
            }
            builder.append(str.substring(prev, pos));
            c = str.charAt(pos + 1);
            s = map.get(c);
            if (s == null) {
                throw new IllegalArgumentException("unknown variable: %" + c);
            }
            builder.append(s);
            prev = pos + 2;
        }
    }

}
