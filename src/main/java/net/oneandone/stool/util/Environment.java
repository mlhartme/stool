/**
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

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Used instead of System.getenv. Allows to track changes and provides a simple mocking mechanism for integration tests */
public class Environment {
    /**
     * To handle special shell variables like PS1 (which is private and cannot be exported on Mac OS), the launch script exports
     * them with this prefix.
     */
    private static final String EXPORTED = "EXPORTED_";

    public static final String STOOL_HOME = "STOOL_HOME";
    public static final String STOOL_SELECTED = "STOOL_SELECTED";
    // TODO: dump when pws uses stagehost instead
    public static final String MACHINE = "MACHINE";
    public static final String STAGE_HOST = "STAGE_HOST";
    public static final String JAVA_HOME = "JAVA_HOME";
    public static final String MAVEN_OPTS = "MAVEN_OPTS";

    public static final String PS1 = "PS1";
    public static final String PWD = "PWD";

    public static Environment loadSystem() {
        Environment result;
        Map<String, String> system;
        String key;
        String value;
        String oldValue;

        result = new Environment();
        system = System.getenv();
        for (Map.Entry<String, String> entry : system.entrySet()) {
            key = entry.getKey();
            if (key.startsWith(EXPORTED)) {
                key = key.substring(EXPORTED.length());
            } else if (system.containsKey(EXPORTED + key)) {
                // skip this key - we'll use (or already have used) the exported key
                continue;
            }
            value = entry.getValue();
            oldValue = result.set(key, value);
            if (oldValue != null) {
                if (!oldValue.equals(value)) {
                    throw new IllegalStateException(key + ": " + value + " vs " + oldValue);
                }
                throw new IllegalStateException("duplicate assignment: " + key + "=" + value);
            }
        }
        return result;
    }

    public static String backupKey(String key) {
        return "STOOL_BACKUP_" + key;
    }

    //--

    private final Map<String, String> properties;

    public Environment() {
        this.properties = new HashMap<>();
    }

    public String set(String key, String value) {
        return properties.put(key, value);
    }

    public void setAll(Environment from) {
        for (Map.Entry<String, String> entry : from.properties.entrySet()) {
            set(entry.getKey(), entry.getValue());
        }
    }

    public String get(String key) {
        return properties.get(key);
    }

    public String code(String key) {
        String value;

        value = get(key);
        if (key.equals(PWD)) {
            return "cd '" + value + "'";
        } else {
            if (value != null) {
                return "export " + key + "='" + value + "'";
            } else {
                return "unset " + key;
            }
        }
    }

    public int hashCode() {
        return properties.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof Environment) {
            return properties.equals(((Environment) obj).properties);
        }
        return false;
    }

    public String toString() {
        return properties.toString();
    }

    //--

    public Environment load(String... keys) {
        Environment result;

        result = new Environment();
        for (String key : keys) {
            result.set(key, get(key));
        }
        return result;
    }

    public void save(Launcher launcher) {
        String value;

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            value = entry.getValue();
            if (value != null) {
                launcher.env(entry.getKey(), value);
            } else {
                launcher.getBuilder().environment().remove(entry.getKey());
            }
        }
    }

    public Set<String> keys() {
        return properties.keySet();
    }

    public Map<String, String> map() {
        return Collections.unmodifiableMap(properties);
    }

    //-- home handling. CAUTION: not part of the session

    public FileNode stoolHome(World world) {
        String str;

        str = get(STOOL_HOME);
        if (str == null) {
            throw new IllegalStateException();
        }
        return world.file(str);
    }

    public void setStoolHome(FileNode home) {
        set(STOOL_HOME, home.getAbsolute());
    }

    //-- proxyOpts

    public String proxyOpts(boolean quote) {
        return proxyOpts(quote, get("http_proxy"), get("https_proxy"), get("no_proxy"));
    }

    /**
     * Turn proxy configuration from http_proxy and no_proxy environment variables into the respective Java properties. See
     * - http://wiki.intranet.1and1.com/bin/view/UE/HttpProxy
     * - http://info4tech.wordpress.com/2007/05/04/java-http-proxy-settings/
     * - http://download.oracle.com/javase/6/docs/technotes/guides/net/proxies.html
     * - http://docs.oracle.com/javase/7/docs/api/java/net/doc-files/net-properties.html#Proxies
     * @param quote is for tomcat 7 - it evals the command line
     */
    public static String proxyOpts(boolean quote, String httpProxy, String httpsProxy, String noProxy) {
        StringBuilder result;

        result = new StringBuilder();
        proxy("http", quote, httpProxy, noProxy, result);
        proxy("https", quote, httpsProxy, noProxy, result);
        return result.toString();
    }

    private static void proxy(String prefix, boolean quote, String proxy, String noProxy, StringBuilder result) {
        URI uri;
        int port;
        boolean first;

        if (proxy != null) {
            try {
                uri = new URI(proxy);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("invalid value for http_proxy: " + proxy, e);
            }
            result.append(" -D").append(prefix).append(".proxyHost=").append(uri.getHost());
            port = uri.getPort();
            if (port == -1) {
                port = 80;
            }
            result.append(" -D").append(prefix).append(".proxyPort=").append(port);
            if (noProxy != null) {
                first = true;
                // CAUTION: single quotes protect the '|' within catalina's start script.
                for (String entry : Separator.COMMA.split(noProxy)) {
                    if (first) {
                        result.append(' ');
                        if (quote) {
                            result.append('\'');
                        }
                        result.append("-D").append(prefix).append(".nonProxyHosts=");
                        first = false;
                    } else {
                        result.append("|");
                    }
                    if (entry.startsWith(".")) {
                        result.append('*');
                    }
                    result.append(entry);
                }
                if (!first && quote) {
                    result.append('\'');
                }
            }
        }
    }

    public String substitute(String str) {
        StringBuilder builder;
        char c;
        int behind;
        String key;
        String value;

        if (str.indexOf('$') == -1) {
            return str;
        }
        builder = new StringBuilder();
        for (int i = 0, max = str.length(); i < max; i++) {
            c = str.charAt(i);
            if (c == '$') {
                if (i + 1 < max && str.charAt(i + 1) == '$') {
                    builder.append(c);
                    i++;
                } else {
                    behind = behind(str, i + 1);
                    key = str.substring(i + 1, behind);
                    value = get(key);
                    if (value == null) {
                        throw new IllegalArgumentException("variable not found: " + key);
                    }
                    builder.append(value);
                    i = behind - 1;
                }
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    public int behind(String str, int idx) {
        int max;
        char c;

        max = str.length();
        while (idx < max) {
            c = str.charAt(idx);
            if (!Character.isAlphabetic(c) && !Character.isDigit(c) && "_".indexOf(c) == -1) {
                break;
            }
            idx++;
        }
        return idx;
    }
}
