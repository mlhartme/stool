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
import net.oneandone.stool.Config;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class StoolConfiguration extends BaseConfiguration {
    /** may be separate port or part of the portFirst ... portLast range; has to end with 2 to use single-sign-on */
    public int portOverview;

    public int portFirst;

    public int portLast;

    /**
     * ps1 shell string
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
     * Name + email
     */
    public String contactAdmin;

    /**
     * group which defines administrative rights
     */
    public String adminGroup;

    /**
     * security level [local|pearl|gem|waterloo]
     */
    public SecurityLevel security;

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

    // number of days between up-to-data checks
    public int updateInterval;

    /**
     * Number of days to wait before removing an expired stage.
     */
    public int autoRemove;

    /**
     * url or null
     */
    public String errorTool;

    public StoolConfiguration() {
        portFirst = 9000;
        portLast = 9999;
        portOverview = portFirst;
        baseHeap = 200;
        basePerm = 60;
        prompt = "{\\+} \\u@\\h:\\w$ ";
        hostname = "localhost";
        diskMin = 2000;
        contactAdmin = "";
        autoRemove = -1;
        security = SecurityLevel.LOCAL;
        macros = new LinkedHashMap<>();

        if (OS.CURRENT == OS.MAC) {
            adminGroup = "everyone";
        } else {
            adminGroup = "users";
        }
        ldapUrl = "";
        ldapPrincipal = "";
        ldapCredentials = "";
        ldapSso = "";
        mailHost = "";
        mailUsername = "";
        mailPassword = "";
        certificates = "";
        updateInterval = 0;
    }

    public static FileNode configurationFile(FileNode home) {
        return home.isDirectory() ? home.join("config.json") : home;
    }

    public static StoolConfiguration load(Gson gson, FileNode home) throws IOException {
        return gson.fromJson(configurationFile(home).readString(), StoolConfiguration.class);
    }

    public void save(Gson gson, FileNode home) throws IOException {
        configurationFile(home).writeString(gson.toJson(this, StoolConfiguration.class));
    }

    public void setDefaults(Map<String, Config.Property> properties, StageConfiguration configuration, String url) throws IOException {
        Config.Property property;

        for (Map.Entry<String, Map<String, String>> outer : defaults.entrySet()) {
            if (url.startsWith(outer.getKey())) {
                for (Map.Entry<String, String> inner : outer.getValue().entrySet()) {
                    property = properties.get(inner.getKey());
                    if (property == null) {
                        throw new IllegalStateException("unknown property: " + inner.getKey());
                    }
                    try {
                        property.set(configuration, inner.getValue());
                    } catch (NoSuchFieldException e) {
                        throw new IllegalStateException("TODO: " + inner.getKey(), e);
                    }
                }
            }
        }
    }
}
