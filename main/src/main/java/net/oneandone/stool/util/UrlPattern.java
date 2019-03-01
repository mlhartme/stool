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
package net.oneandone.stool.util;

import net.oneandone.inline.ArgumentException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UrlPattern {
    private static final String CONTEXT = "!";

    public static UrlPattern parse(String url) {
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
        return new UrlPattern(protocol, hostname, context, path);
    }

    //--

    private final String protocol;
    private final String hostname;
    /** may be null */
    private final String context;
    private final String path;

    public UrlPattern(String protocol, String hostname, String context, String path) {
        this.protocol = protocol;
        this.hostname = hostname;
        this.context = context;
        this.path = path;
    }

    public String getContext() {
        return context;
    }

    public UrlPattern substitute(String app, String stage, String hostname) {
        Map<Character, String> map;

        map = new HashMap<>();
        map.put('a', app);
        map.put('s', stage);
        map.put('h', hostname);
        map.put('p', "%p");
        return substitute(map);
    }

    public UrlPattern substitute(Map<Character, String> map) {
        return new UrlPattern(Subst.subst(protocol, map), Subst.subst(hostname, map), context == null ? null : Subst.subst(context, map), Subst.subst(path, map));
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

    //--

    public static Map<String, String> urlMap(String app, String stageName, String hostname, int httpPort, int httpsPort, String url) {
        Map<String, String> result;

        result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : doMap(app, stageName, hostname, httpPort, httpsPort, url).entrySet()) {
            result.put(entry.getKey(), hideContextMarker(entry.getValue()));
        }
        return result;
    }

    private static String hideContextMarker(String url) {
        int beforeHost;
        int afterHost;
        int context;

        beforeHost = url.indexOf("://");
        if (beforeHost == -1) {
            return url;
        }
        afterHost = url.indexOf("/", beforeHost + 3);
        if (afterHost == -1) {
            return url;
        }
        context = url.indexOf("//", afterHost + 1);
        if (context == -1) {
            return url;
        }
        return url.substring(0, context) + url.substring(context + 1);
    }

    private static Map<String, String> doMap(String name, String stageName, String hostname, int httpPort, int httpsPort, String url) {
        Map<String, String> result;
        List<String> all;
        List<String> http;
        List<String> https;

        result = new LinkedHashMap<>();
        all = UrlPattern.parse(url).substitute(name, stageName, hostname).map();
        http = new ArrayList<>();
        https = new ArrayList<>();
        for (String u : all) {
            if (u.startsWith("https:")) {
                https.add(u.replace("%p", Integer.toString(httpsPort)));
            } else {
                http.add(u.replace("%p", Integer.toString(httpPort)));
            }
        }
        add(name, "", http, result);
        add(name, " SSL", https, result);
        return result;
    }

    private static void add(String nameBase, String nameSuffix, List<String> all, Map<String, String> result) {
        String name;
        int no;

        no = 0;
        for (String u : all) {
            if (all.size() > 1) {
                no++;
                name = nameBase + "-" + no;
            } else {
                name = nameBase;
            }
            name = name + nameSuffix;
            result.put(name, u);
        }
    }
}
