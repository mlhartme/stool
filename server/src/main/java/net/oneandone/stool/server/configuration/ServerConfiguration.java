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

import net.oneandone.stool.server.util.Mailer;
import net.oneandone.sushi.fs.file.FileNode;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.UnknownHostException;
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

    /** path on the docker host */
    public FileNode serverHome;

    public String loglevel;

    public String registryNamespace;

    public int portFirst;

    public int portLast;

    /**
     * used for output and application urls
     */
    public String hostname;

    /**
     * Use vhosts
     */
    public boolean vhosts;

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

    /**
     * Number of days to wait before removing an expired stage.
     */
    public int autoRemove;

    public int memoryQuota;

    public int quota;

    /** absolute path to secrets root */
    public String secrets;

    // default environment for every container
    public Map<String, String> environment;

    public ServerConfiguration() {
        serverHome = null;
        loglevel = "INFO";
        registryNamespace = "main";
        portFirst = 9000;
        portLast = 9999;
        hostname = "localhost";
        admin = "";
        autoRemove = -1;
        ldapUrl = "";
        ldapPrincipal = "";
        ldapCredentials = "";
        ldapUnit = "";
        mailHost = "";
        mailUsername = "";
        mailPassword = "";
        memoryQuota = 10000;
        quota = 0;
        secrets = "/etc/fault/workspace";
        environment = new HashMap<>();
    }

    public void loadEnv() {
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

    public void verfiyHostname() throws UnknownHostException {
        InetAddress.getByName(hostname);
    }

    public boolean auth() {
        return !ldapUrl.isEmpty();
    }
}
