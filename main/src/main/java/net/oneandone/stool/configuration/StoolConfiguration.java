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
package net.oneandone.stool.configuration;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.oneandone.stool.util.Mailer;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

public class StoolConfiguration {
    public static Map<String, Property> properties() {
        Map<String, Property> result;

        result = new LinkedHashMap<>();
        for (Field field : StoolConfiguration.class.getFields()) {
            result.put(field.getName(), new Property(field.getName(), "stool property", field, null));
        }
        return result;
    }

    //--

    public int portFirst;

    public int portLast;

    /**
     * ps1 shell string with extra variables:
     *   \\+ for stage indicator
     *   \\= for original prompt
     */
    public String prompt;

    /**
     * used for output and application urls
     */
    public String hostname;

    /**
     * Use vhosts
     */
    public boolean vhosts;

    /**
     * in megabyte
     */
    public int diskMin;

    /**
     * Name + email. Used for problem reports, feedback emails,
     */
    public String contactAdmin;

    /**
     * true if multiple users work on stages
     */
    public boolean shared;

    /**
     * true if users have to commit all source changes before stool allows them to start the stage.
     */
    public boolean committed;

    /**
     * For additional "system-wide" shortcuts.
     */
    public Map<String, String> macros;

    // preserve order!
    public LinkedHashMap<String, Map<String, String>> defaults = new LinkedHashMap<>();

    /**
     * Base value to calculate ram per application
     */
    public int basePerm;

    /**
     * Base value to calculate ram per application
     */
    public int baseHeap;

    public String mailHost;

    public String mailUsername;

    public String mailPassword;

    // URL to generate certificates
    public String certificates;

    public String ldapUrl;

    public String ldapPrincipal;

    public String ldapCredentials;

    public String ldapSso;

    /**
     * Number of days to wait before removing an expired stage.
     */
    public int autoRemove;

    public String downloadTomcat;

    public String downloadServiceWrapper;

    public FileNode downloadCache;

    public StoolConfiguration(FileNode downloadCache) {
        portFirst = 9000;
        portLast = 9999;
        baseHeap = 200;
        basePerm = 60;
        prompt = "{\\+} \\=";
        hostname = "localhost";
        diskMin = 2000;
        contactAdmin = "";
        autoRemove = -1;
        shared = false;
        committed = false;
        macros = new LinkedHashMap<>();
        ldapUrl = "";
        ldapPrincipal = "";
        ldapCredentials = "";
        ldapSso = "";
        mailHost = "";
        mailUsername = "";
        mailPassword = "";
        certificates = "";
        downloadTomcat =  "http://archive.apache.org/dist/tomcat/tomcat-${major}/v${version}/bin/apache-tomcat-${version}.tar.gz";
        downloadServiceWrapper = "http://wrapper.tanukisoftware.com/download/${version}/wrapper-"
                + (OS.CURRENT == OS.LINUX ? "linux-x86-64" : "macosx-universal-64") + "-${version}.tar.gz";
        this.downloadCache = downloadCache;
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

    public void setDefaults(Map<String, Property> properties, StageConfiguration configuration, String url) {
        Property property;

        for (Map.Entry<String, Map<String, String>> outer : defaults.entrySet()) {
            if (url.startsWith(outer.getKey())) {
                for (Map.Entry<String, String> inner : outer.getValue().entrySet()) {
                    property = properties.get(inner.getKey());
                    if (property == null) {
                        throw new IllegalStateException("unknown property: " + inner.getKey());
                    }
                    property.set(configuration, inner.getValue());
                }
            }
        }
    }

    public StoolConfiguration createPatched(Gson gson, String str) {
        JsonObject changes;
        JsonObject dest;

        dest = (JsonObject) gson.toJsonTree(this);
        changes = (JsonObject) new JsonParser().parse(str);
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
}
