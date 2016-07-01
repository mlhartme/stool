package net.oneandone.stool.util;

import net.oneandone.inline.ArgumentException;
import net.oneandone.sushi.util.Separator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Url {
    private static final String CONTEXT = "!";

    public static Url parse(String url) {
        int beforeHost;
        int afterHost;
        String protocol;
        String hostname;
        String path;
        int beforePath;
        String context;

        beforeHost = url.indexOf("://");
        if (beforeHost == -1) {
            throw new ArgumentException(url);
        }
        protocol = url.substring(0, beforeHost);
        afterHost = url.indexOf('/', beforeHost + 3);
        if (afterHost == -1) {
            afterHost = url.length();
            path = "";
            context = null;
        } else {
            beforePath = url.indexOf(CONTEXT, afterHost + 1);
            if (beforePath == -1) {
                beforePath = afterHost + 1;
                context = null;
            } else {
                context = url.substring(afterHost + 1, beforePath);
                beforePath++;
            }
            path = url.substring(beforePath);
        }
        hostname = url.substring(beforeHost + 3, afterHost);
        return new Url(protocol, hostname, context, path);
    }

    private static final Separator SEP = Separator.on('|').trim();

    //--

    private final String protocol;
    private final String hostname;
    /** may be null */
    private final String context;
    private final String path;

    public Url(String protocol, String hostname, String context, String path) {
        this.protocol = protocol;
        this.hostname = hostname;
        this.context = context;
        this.path = path;
    }

    public Url sustitute(Map<Character, String> map) {
        return new Url(subst(protocol, map), subst(hostname, map), context == null ? null : subst(context, map), subst(path, map));
    }

    private static String subst(String str, Map<Character, String> map) {
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

    public String toString() {
        return protocol + "://" + hostname + "/" + (context == null ? "" : context + CONTEXT) + path;
    }

    public List<String> map() {
        MultiString multiString;

        multiString = new MultiString();
        multiString.append(protocol);
        multiString.append("://");
        multiString.append(hostname);
        multiString.append("/");
        if (context != null) {
            multiString.append(context + CONTEXT);
        }
        multiString.append(path);
        return multiString.lst;
    }

    public boolean ssl() {
        MultiString multiString;

        multiString = new MultiString();
        multiString.append(protocol);
        return multiString.contains("https");
    }

    //--

    public static class MultiString {
        public final List<String> lst;

        public MultiString() {
            lst = new ArrayList<>();
            lst.add("");
        }

        public void append(String str) {
            int prev;
            int open;
            int close;
            List<String> tmp;

            prev = 0;
            while (true) {
                open = str.indexOf('(', prev);
                if (open == -1) {
                    appendAll(str.substring(prev, str.length()));
                    return;
                }
                open++;
                close = str.indexOf(')', open);
                if (close == -1) {
                    throw new IllegalArgumentException("closing ) not found: " + str);
                }
                tmp = new ArrayList<>(lst);
                lst.clear();
                for (String p : SEP.split(str.substring(open, close))) {
                    for (String a : tmp) {
                        lst.add(a + p);
                    }
                }
                prev = close + 1;
            }
        }

        private void appendAll(String str) {
            for (int i = 0; i < lst.size(); i++) {
                lst.set(i, lst.get(i) + str);
            }
        }

        public boolean contains(String str) {
            return lst.contains(str);
        }
    }
}
