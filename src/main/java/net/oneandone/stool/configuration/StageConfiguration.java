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
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import net.oneandone.stool.configuration.adapter.UntilTypeAdapter;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Role;
import net.oneandone.sushi.fs.ExistsException;
import net.oneandone.sushi.fs.Node;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class StageConfiguration extends BaseConfiguration {
    @Expose
    @Option(key = "mode", description = "mode to run applications with")
    public String mode;

    @Expose
    @Option(key = "cookies", description = "use cookies for tomcat", role = Role.USER)
    public Boolean cookies;

    @Expose
    @Option(key = "build", description = "arbitrary build command line. Supported variables: @directory@", role = Role.USER)
    public String build;

    @Expose
    @Option(key = "port.prefix", description = "do not change this!")
    public Ports ports;

    @Expose
    @Option(key = "tomcat.opts", description = "CATALINE_OPTS without heap/perm settings")
    public String tomcatOpts;

    @Expose
    @Option(key = "tomcat.version", description = "Tomcat version to use.")
    public String tomcatVersion;

    @Expose
    @Option(key = "tomcat.service", description = "Java Service Wrapper version to use around Tomcat.")
    public String tomcatService;

    @Expose
    @Option(key = "tomcat.heap", description = "memory in mb")
    public Integer tomcatHeap;

    @Expose
    @Option(key = "tomcat.perm", description = "memory in mb")
    public Integer tomcatPerm;

    @Expose
    @Option(key = "tomcat.select", description = "hostnames to start - empty for all", role = Role.USER)
    public List<String> tomcatSelect;

    @Expose
    @Option(key = "java.home", description = "jdk or jre directory")
    public String javaHome;

    @Expose
    @Option(key = "maven.opts",
      description = "MAVEN_OPTS when building this stage. Supported variables: @trustStore@, @proxyOpts@ and @localRepository@")
    public String mavenOpts;

    @Expose
    @Option(key = "until", description = "YYYY-MM-DD and optional time")
    public Until until;

    @Expose
    @Option(key = "suffix", description = "suffix for the link eg. http://1and1.com/{suffix}", role = Role.USER)
    public String suffix;

    @Expose
    @Option(key = "sslUrl", description = "overrides the default url for certificate creation")
    public String sslUrl;

    @Expose
    @Option(key = "autoRefresh", description = "true if a stage should care about refreshing by itself")
    public Boolean autoRefresh;

    @Expose
    @Option(key = "comment", description = "a comment")
    public String comment;


    public StageConfiguration(Ports ports, String javaHome) {
        this.mode = "test";
        this.cookies = true;
        this.build = "false";
        this.ports = ports;
        this.tomcatOpts = "";
        this.tomcatVersion = "7.0.57";
        this.tomcatService = "3.5.26";
        this.tomcatHeap = 200;
        this.tomcatPerm = 64;
        this.tomcatSelect = new ArrayList<>();
        this.javaHome = javaHome;
        this.mavenOpts = "";
        this.until = Until.reserved();
        this.suffix = "";
        this.sslUrl = "";
        this.comment = "";
        this.autoRefresh = false;
    }

    public static boolean isConfigurable(String key, Role role) {
        for (Field field : StageConfiguration.class.getDeclaredFields()) {
            if (field.isAnnotationPresent(Option.class) && field.getAnnotation(Option.class).key().equals(key)) {
                securityCheck(field, role);
                return true;
            }
        }
        return false;
    }

    private static void securityCheck(Field field, Role role) {
        if (role == null) {
            return;
        }
        Option option = field.getAnnotation(Option.class);
        if (option.role().compareTo(role) < 0) {
            throw new SecurityException(Role.ERROR);
        }
    }

    public static StageConfiguration load(Node wrapper) throws IOException {
        try {
            return gson().fromJson(configurationFile(wrapper).readString(), StageConfiguration.class);
        } catch (IOException e) {
            throw new IOException(e);
        }
    }

    public static Node configurationFile(Node wrapper) throws ExistsException {
        return wrapper.isDirectory() ? wrapper.join("config.json") : wrapper;
    }

    public static Gson gson() {
        return new GsonBuilder()
          .registerTypeAdapter(Until.class, new UntilTypeAdapter())
          .registerTypeAdapter(Ports.class, new Ports.PortsTypeAdapter())
          .setPrettyPrinting()
          .create();
    }

    public void save(Node wrapper) throws IOException {
        configurationFile(wrapper).writeString(gson().toJson(this));
    }
}

