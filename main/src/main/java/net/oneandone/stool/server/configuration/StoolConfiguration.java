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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.oneandone.stool.server.util.Mailer;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class StoolConfiguration {
    public static Map<String, Accessor> properties() {
        Map<String, Accessor> result;

        result = new LinkedHashMap<>();
        for (Field field : StoolConfiguration.class.getFields()) {
            result.put(field.getName(), new ReflectAccessor(field.getName(), field));
        }
        return result;
    }

    //--

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

    /**
     * Base value to calculate ram per application
     */
    public int baseMemory;

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

    public int quota;

    /** Path to docker unix domain socket */
    public String docker;

    /** absolute path to secrets root */
    public String secrets;

    public FileNode certificate;

    // default environment for every container
    public Map<String, String> environment;

    public StoolConfiguration() {
        registryNamespace = "main";
        portFirst = 9000;
        portLast = 9999;
        baseMemory = 400;
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
        quota = 0;
        docker = "/var/run/docker.sock";
        secrets = "/etc/fault/workspace";
        certificate = null;
        environment = new HashMap<>();
    }

    public static FileNode configurationFile(FileNode home) {
        return home.join("config.json");
    }

    public static StoolConfiguration load(Gson gson, FileNode home) throws IOException {
        return loadFile(gson, configurationFile(home));
    }

    public static StoolConfiguration loadFile(Gson gson, FileNode file) throws IOException {
        return gson.fromJson(file.readString(), StoolConfiguration.class);
    }

    public void save(Gson gson, FileNode home) throws IOException {
        configurationFile(home).writeString(gson.toJson(this, StoolConfiguration.class));
    }

    public StoolConfiguration createPatched(Gson gson, String str) {
        JsonObject changes;
        JsonObject dest;

        dest = (JsonObject) gson.toJsonTree(this);
        changes = new JsonParser().parse(str).getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : changes.entrySet()) {
            if (!dest.has(entry.getKey())) {
                throw new IllegalStateException("unknown property: " + entry.getKey());
            }
            dest.add(entry.getKey(), entry.getValue());
        }
        return gson.fromJson(dest, StoolConfiguration.class);
    }

    public Mailer mailer() {
        return new Mailer(mailHost, mailUsername, mailPassword);
    }

    public void verfiyHostname() throws UnknownHostException {
        InetAddress.getByName(hostname);
    }
}
