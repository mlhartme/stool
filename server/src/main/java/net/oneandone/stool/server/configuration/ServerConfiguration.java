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

import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.kubernetes.Registry;
import net.oneandone.stool.server.ArgumentException;
import net.oneandone.stool.server.util.Mailer;
import net.oneandone.stool.server.util.Pool;

import java.io.IOException;
import java.lang.reflect.Field;
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

    private String registryPrefix;

    public int portFirst;

    public int portLast;

    /**
     * used for output and application urls
     */
    public String dockerHost;

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

    public String ldapSso;

    /**
     * Number of days to wait before removing an expired stage.
     */
    public int autoRemove;

    public int memoryQuota;

    public int diskQuota;

    public String jmxUsage;

    public boolean engineLog;

    public int defaultExpire;

    // default environment for every container
    public Map<String, String> environment;

    public ServerConfiguration() {
        vhosts = false;
        loglevel = "INFO";
        registryPrefix = Registry.LOCAL_HOST + "/";
        portFirst = 31000;
        portLast = 31999;
        dockerHost = "localhost";
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
        memoryQuota = 0;
        diskQuota = 0;
        jmxUsage = "jconsole localhost:%i";
        engineLog = false;
        defaultExpire = 0;
        environment = new HashMap<>();
    }

    public String registryHost() {
        int idx;

        idx = registryPrefix.indexOf('/');
        return registryPrefix.substring(0, idx);
    }

    public String registryPathPrefix() {
        int idx;

        idx = registryPrefix.indexOf('/');
        return registryPrefix.substring(idx + 1);
    }

    // this is to avoid engine 500 error reporting "invalid reference format: repository name must be lowercase"
    public void validateRegistryPrefix() {
        if (!registryPrefix.endsWith("/")) {
            throw new ArgumentException("invalid registry prefix: " + registryPrefix);
        }
        for (int i = 0, length = registryPrefix.length(); i < length; i++) {
            if (Character.isUpperCase(registryPrefix.charAt(i))) {
                throw new ArgumentException("invalid registry prefix: " + registryPrefix);
            }
        }
    }

    public Pool loadPool(Engine engine) throws IOException {
        return Pool.load(engine, portFirst + 4 /* 4 ports reserved for the server (http(s), debug, jmx, unused) */, portLast);
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

    // TODO: currently has no wire log -- use for docker ...
    public String engineLogFile() {
        return engineLog ? "/var/lib/stool/logs/engine.log" : null;
    }
}
