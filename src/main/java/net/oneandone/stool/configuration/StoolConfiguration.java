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

import com.github.zafarkhaja.semver.Version;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import net.oneandone.stool.configuration.adapter.FileNodeTypeAdapter;
import net.oneandone.stool.configuration.adapter.UntilTypeAdapter;
import net.oneandone.stool.configuration.adapter.VersionTypeAdapter;
import net.oneandone.stool.util.Ports;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class StoolConfiguration extends BaseConfiguration {
    @Expose
    public Ports portPrefixFirst;

    @Expose
    public Ports portPrefixLast;
    /**
     * ps1 shell string
     */
    @Expose
    public String prompt;

    /**
     * used for output and application urls
     */
    @Expose
    public String hostname;

    /**
     * in megabyte
     */
    @Expose
    public int diskMin;

    /**
     * Name + email
     */
    @Expose
    public String contactAdmin;

    /**
     * group which defines administrative rights
     */
    @Expose
    public String adminGroup;

    /**
     * security level [local|pearl|gem|waterloo]
     */
    @Expose
    public SecurityLevel security;

    @Expose
    public Map<String, String> macros;

    @Expose
    public Map<String, StageConfiguration> defaults = new LinkedHashMap<>();

    /**
     * Base value to calculate ram per application
     */
    @Expose
    public int basePerm;

    /**
     * Base value to calculate ram per application
     */
    @Expose
    public int baseHeap;

    @Expose
    public String ldapUrl;

    @Expose
    public String ldapPrincipal;

    @Expose
    public String ldapCredentials;

    @Expose
    public String mailHost;

    @Expose
    public String mailUsername;

    @Expose
    public String mailPassword;

    /**
     * Number of days to wait before removing an expired stage.
     */
    @Expose
    public int autoRemove;

    public StoolConfiguration() throws IOException {
        portPrefixFirst = new Ports(900); // avoid clash with default tomcat port 8080
        portPrefixLast = new Ports(999);
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
        mailHost = "";
        mailUsername = "";
        mailPassword = "";
    }

    // TODO: doesn't work in integration tests
    public static Version version() throws IOException {
        String str;

        str = StoolConfiguration.class.getPackage().getSpecificationVersion();
        return Version.valueOf(String.valueOf(str));
    }

    private static FileNode configurationFile(FileNode home) {
        return home.isDirectory() ? home.join("config.json") : home;
    }

    public static StoolConfiguration load(FileNode home) throws IOException {
        return gson(home.getWorld()).fromJson(configurationFile(home).readString(), StoolConfiguration.class);
    }
    private static Gson gson(World world) {
        return new GsonBuilder()
          .registerTypeAdapter(FileNode.class, new FileNodeTypeAdapter(world))
          .registerTypeAdapter(Version.class, new VersionTypeAdapter())
          .registerTypeAdapter(Until.class, new UntilTypeAdapter())
          .registerTypeAdapter(Ports.class, new Ports.PortsTypeAdapter())
          .excludeFieldsWithoutExposeAnnotation()
          .disableHtmlEscaping()
          .setPrettyPrinting()
          .create();
    }

    public void addDefaults(String javaHome, String wsdtoolsHome) {
        StageConfiguration defaultConfiguration;
        StageConfiguration controlpanelConfiguration;
        String mavenOpts = "-Dmaven.repo.local=@localRepository@ @proxyOpts@ @trustStore@";

        defaultConfiguration = new StageConfiguration(new Ports(0), javaHome);
        defaultConfiguration.tomcatHeap = 0;
        defaultConfiguration.tomcatPerm = 0;
        defaultConfiguration.tomcatOpts = "@proxyOpts@ @trustStore@";
        defaultConfiguration.mavenOpts = mavenOpts;
        defaultConfiguration.build = "";
        defaultConfiguration.mode = "test";
        defaults.put("", defaultConfiguration);


        controlpanelConfiguration = new StageConfiguration(new Ports(0), javaHome);
        controlpanelConfiguration.build = "mvn clean install -Ppublish  -U -B";
        controlpanelConfiguration.tomcatHeap = 2000;
        controlpanelConfiguration.tomcatPerm = 512;
        controlpanelConfiguration.suffix = "/xml/config";
        controlpanelConfiguration.mode = "test";
        controlpanelConfiguration.mavenOpts = "-Xmx2500m -XX:MaxPermSize=512m " + mavenOpts;
        defaults.put("https://svn.1and1.org/svn/PFX/controlpanel/", controlpanelConfiguration);


        macros.put("trustStore", "-Djavax.net.ssl.trustStore=" + Strings.removeRightOpt(wsdtoolsHome, "/") + "/cacerts");
    }

    public void save(FileNode home) throws IOException {
        configurationFile(home).writeString(gson(home.getWorld()).toJson(this, StoolConfiguration.class));
    }

    /**
     * the configuration itself should know what has to be cleaned up
     */
    public void cleanup() {
    }
}
