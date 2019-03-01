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

    public UrlPattern sustitute(Map<Character, String> map) {
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
}
