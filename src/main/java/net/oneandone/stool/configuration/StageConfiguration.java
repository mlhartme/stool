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
import net.oneandone.stool.extensions.Extensions;
import net.oneandone.stool.util.Role;
import net.oneandone.sushi.fs.ExistsException;
import net.oneandone.sushi.fs.Node;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class StageConfiguration extends BaseConfiguration {
    @Option(key = "mode", description = "mode to run applications with")
    public String mode;

    @Option(key = "cookies", description = "use cookies for tomcat", role = Role.USER)
    public Boolean cookies;

    @Option(key = "prepare", description = "execute this after checkout", role = Role.USER)
    public String prepare;

    @Option(key = "build", description = "arbitrary build command line. Supported variables: @directory@", role = Role.USER)
    public String build;

    @Option(key = "refresh", description = "execute this for refresh", role = Role.USER)
    public String refresh;

    @Option(key = "pom", description = "pom file name", role = Role.USER)
    public String pom;

    @Option(key = "tomcat.opts", description = "CATALINE_OPTS without heap/perm settings")
    public String tomcatOpts;

    @Option(key = "tomcat.version", description = "Tomcat version to use.")
    public String tomcatVersion;

    @Option(key = "tomcat.service", description = "Java Service Wrapper version to use around Tomcat.")
    public String tomcatService;

    @Option(key = "tomcat.heap", description = "memory in mb")
    public Integer tomcatHeap;

    @Option(key = "tomcat.perm", description = "memory in mb")
    public Integer tomcatPerm;

    @Option(key = "tomcat.select", description = "hostnames to start - empty for all", role = Role.USER)
    public List<String> tomcatSelect;

    @Option(key = "java.home", description = "jdk or jre directory")
    public String javaHome;

    @Option(key = "maven.home", description = "Maven home")
    public String mavenHome;

    @Option(key = "maven.opts",
      description = "MAVEN_OPTS when building this stage. Supported variables: @trustStore@, @proxyOpts@ and @localRepository@")
    public String mavenOpts;

    @Option(key = "until", description = "YYYY-MM-DD and optional time")
    public Until until;

    @Option(key = "suffix", description = "suffix for the link eg. http://1and1.com/{suffix}", role = Role.USER)
    public String suffix;

    @Option(key = "sslUrl", description = "overrides the default url for certificate creation")
    public String sslUrl;

    @Option(key = "autoRefresh", description = "true if a stage should care about refreshing by itself")
    public Boolean autoRefresh;

    @Option(key = "comment", description = "a comment")
    public String comment;

    public final Extensions extensions;

    public StageConfiguration(String javaHome, String mavenHome, Extensions extensions) {
        this.mode = "test";
        this.cookies = true;
        this.prepare = "";
        this.build = "false";
        this.refresh = "svn up";
        this.pom = "pom.xml";
        this.tomcatOpts = "";
        this.tomcatVersion = "7.0.57";
        this.tomcatService = "3.5.26";
        this.tomcatHeap = 200;
        this.tomcatPerm = 64;
        this.tomcatSelect = new ArrayList<>();
        this.javaHome = javaHome;
        this.mavenHome = mavenHome;
        this.mavenOpts = "";
        this.until = Until.reserved();
        this.suffix = "";
        this.sslUrl = "";
        this.comment = "";
        this.autoRefresh = false;
        this.extensions = extensions;
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

    public static StageConfiguration load(Gson gson, Node wrapper) throws IOException {
        try (Reader reader = configurationFile(wrapper).createReader()) {
            return gson.fromJson(reader, StageConfiguration.class);
        }
    }

    public static Node configurationFile(Node wrapper) throws ExistsException {
        return wrapper.isDirectory() ? wrapper.join("config.json") : wrapper;
    }

    public void save(Gson gson, Node wrapper) throws IOException {
        configurationFile(wrapper).writeString(gson.toJson(this));
    }
}

