package net.oneandone.stool.util;

import net.oneandone.inline.ArgumentException;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.util.ArrayList;
import java.util.List;

public class Url {
    public static Url parse(String url) {
        int beforeHost;
        int afterHost;
        List<String> protocols;
        List<String> hostnames;
        List<String> paths;

        beforeHost = url.indexOf("://");
        if (beforeHost == -1) {
            throw new ArgumentException(url);
        }
        protocols = list(url.substring(0, beforeHost));
        afterHost = url.indexOf('/', beforeHost + 3);
        if (afterHost == -1) {
            afterHost = url.length();
            paths = new ArrayList<>();
        } else {
            paths = list(url.substring(afterHost + 1));
        }
        hostnames = list(url.substring(beforeHost + 3, afterHost));

        return new Url(protocols, hostnames, "", paths);
    }

    private static final Separator SEP = Separator.on('|').trim();

    private static List<String> list(String str) {
        List<String> result;
        if (str.startsWith("(")) {
            str = Strings.removeRight(str.substring(1), ")");
            result = SEP.split(str);
        } else {
            result = new ArrayList<>();
            result.add(str);
        }
        return result;
    }

    //--

    private final List<String> protocols;
    private final List<String> hostnames;
    private final String context;
    private final List<String> paths;

    public Url(List<String> protocols, List<String> hostnames, String context, List<String> paths) {
        this.protocols = protocols;
        this.hostnames = hostnames;
        this.context = context;
        this.paths = paths;
    }

    public String toString() {
        return str(protocols) + "://" + str(hostnames) + "/" + context + str(paths);
    }

    private static String str(List<String> all) {
        switch (all.size()) {
            case 0:
                return "";
            case 1:
                return all.get(0);
            default:
                return '(' + SEP.join(all) + ')';
        }
    }
}
