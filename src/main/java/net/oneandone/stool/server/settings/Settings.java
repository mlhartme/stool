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
package net.oneandone.stool.server.settings;

import net.oneandone.stool.server.util.Mailer;
import net.oneandone.sushi.util.Separator;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Global configuration */
public class Settings {
    public static Settings load() {
        Settings result;

        result = new Settings();
        result.loadEnv();
        return result;
    }

    public static Map<String, Accessor> properties() {
        Map<String, Accessor> result;

        result = new LinkedHashMap<>();
        for (Field field : Settings.class.getFields()) {
            result.put(field.getName(), new Accessor(field.getName(), field));
        }
        return result;
    }

    //--

    public String loglevel;

    public String registryCredentials;

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

    public int defaultExpire;

    public Settings() {
        fqdn = "localhost";
        kubernetes = "http://localhost";
        loglevel = "INFO";
        registryCredentials = "";
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
        defaultExpire = 0;
    }

    public static class UsernamePassword {
        public final String username;
        public final String password;

        public UsernamePassword(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    private Map<String, UsernamePassword> lazyRegistryCredentials = null;

    public UsernamePassword registryCredentials(String registry) {
        int idx;
        String host;

        if (lazyRegistryCredentials == null) {
            lazyRegistryCredentials = new HashMap<>();
            for (String entry : Separator.COMMA.split(registryCredentials)) {
                idx = entry.indexOf('=');
                if (idx < 0) {
                    throw new IllegalStateException(entry);
                }
                host = entry.substring(0, idx);
                entry = entry.substring(idx + 1);
                idx = entry.indexOf(':');
                if (idx < 0) {
                    throw new IllegalStateException(entry);
                }
                lazyRegistryCredentials.put(host, new UsernamePassword(entry.substring(0, idx), entry.substring(idx + 1)));
            }
        }
        return lazyRegistryCredentials.get(registry);
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

        result = new StringBuilder("Settings {\n");
        for (Map.Entry<String, Accessor> entry : Settings.properties().entrySet()) {
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
