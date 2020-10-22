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
package net.oneandone.stool.server.configuration;

import net.oneandone.stool.server.ArgumentException;
import net.oneandone.stool.server.util.Mailer;
import net.oneandone.sushi.util.Strings;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ServerConfiguration {
    public static ServerConfiguration load() {
        ServerConfiguration result;

        result = new ServerConfiguration();
        result.loadEnv();
        return result;
    }

    public static Map<String, Accessor> properties() {
        Map<String, Accessor> result;

        result = new LinkedHashMap<>();
        for (Field field : ServerConfiguration.class.getFields()) {
            result.put(field.getName(), new ReflectAccessor(field.getName(), field));
        }
        return result;
    }

    //--

    public String loglevel;

    public String registryUrl;

    /**
     * used for output and application urls
     */
    public String fqdn;

    /**
     * public url for kubernetes api -- reported to clients to use temporary service accounts
     */
    public String kubernetes;

    /**
     * Name + email. Used for problem reports, feedback emails,
     */
    public String admin;

    public String mailHost;

    public String mailUsername;

    public String mailPassword;

    public String ldapUrl;

    public String ldapPrincipal;

    public String ldapCredentials;

    public String ldapUnit;

    public String ldapSso;

    /**
     * Number of days to wait before removing an expired stage.
     */
    public int autoRemove;

    public int diskQuota;

    public int defaultExpire;

    // default environment for every stage
    public Map<String, String> environment;

    public ServerConfiguration() {
        fqdn = "localhost";
        kubernetes = "http://localhost";
        loglevel = "INFO";
        registryUrl = "http://localhost:31500/";
        admin = "";
        autoRemove = -1;
        ldapUrl = "";
        ldapPrincipal = "";
        ldapCredentials = "";
        ldapUnit = "";
        ldapSso = "";
        mailHost = "";
        mailUsername = "";
        mailPassword = "";
        diskQuota = 0;
        defaultExpire = 0;
        environment = new HashMap<>();
    }

    public String registryUrl() {
        return registryUrl;
    }

    public String registryPath() {
        String path;

        path = URI.create(registryUrl).getPath();
        path = Strings.removeLeft(path, "/");
        if (!path.isEmpty() && !path.endsWith("/")) {
            path = path + "/";
        }
        return path;
    }

    // this is to avoid engine 500 error reporting "invalid reference format: repository name must be lowercase"
    public void validateRegistryUrl() {
        if (!registryUrl.endsWith("/")) {
            throw new ArgumentException("invalid registry prefix: " + registryUrl);
        }
        URI uri;

        uri = URI.create(registryUrl);
        checkLowercase(uri.getHost());
        checkLowercase(uri.getPath());
    }

    private static void checkLowercase(String str) {
        for (int i = 0, length = str.length(); i < length; i++) {
            if (Character.isUpperCase(str.charAt(i))) {
                throw new ArgumentException("invalid registry prefix: " + str);
            }
        }
    }

    private void loadEnv() {
        String name;
        String str;

        for (Map.Entry<String, Accessor> entry : properties().entrySet()) {
            name = toUpper(entry.getKey());
            str = System.getenv(name);
            if (str != null) {
                entry.getValue().set(this, str);
            }
        }
    }

    private static String toUpper(String str) {
        StringBuilder result;
        char c;
        char upper;

        result = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            c = str.charAt(i);
            upper = Character.toUpperCase(c);
            if (c == upper) {
                result.append('_');
            }
            result.append(upper);
        }
        return result.toString();
    }

    public String toString() {
        StringBuilder result;

        result = new StringBuilder("ServerConfiguration {\n");
        for (Map.Entry<String, Accessor> entry : ServerConfiguration.properties().entrySet()) {
            result.append("  ").append(entry.getKey()).append(": ").append(entry.getValue().get(this)).append('\n');
        }
        result.append("}\n");
        return result.toString();
    }

    public Mailer mailer() {
        return new Mailer(mailHost, mailUsername, mailPassword);
    }

    public boolean auth() {
        return !ldapUrl.isEmpty();
    }
}
