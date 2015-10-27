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
import net.oneandone.stool.extensions.ExtensionsFactory;
import net.oneandone.sushi.fs.ExistsException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StageConfiguration {
    public static FileNode file(FileNode backstage) throws ExistsException {
        return backstage.isDirectory() ? backstage.join("config.json") : backstage;
    }

    public static StageConfiguration load(Gson gson, Node file) throws IOException {
        try (Reader reader = file.createReader()) {
            return gson.fromJson(reader, StageConfiguration.class);
        }
    }

    public static Map<String, Property> properties(ExtensionsFactory extensions) {
        Map<String, Property> result;
        Option option;

        result = new LinkedHashMap<>();
        for (Field field : StageConfiguration.class.getFields()) {
            option = field.getAnnotation(Option.class);
            if (option != null && !option.readOnly()) {
                result.put(option.key(), new Property(option.key(), option.description(), field, null));
            }
        }
        extensions.fields(result);
        return result;
    }

    //--

    @Option(key = "id", description = "unique identifier for this stage", readOnly = true)
    public final String id;

    @Option(key = "cookies", description = "use cookies for tomcat")
    public Boolean cookies;

    @Option(key = "prepare", description = "execute this after checkout")
    public String prepare;

    @Option(key = "build", description = "arbitrary build command line. Supported variables: @directory@")
    public String build;

    @Option(key = "refresh", description = "execute this for refresh")
    public String refresh;

    @Option(key = "pom", description = "pom file name")
    public String pom;

    @Option(key = "tomcat.opts", description = "CATALINA_OPTS without heap/perm settings")
    public String tomcatOpts;

    @Option(key = "tomcat.version", description = "Tomcat version to use.")
    public String tomcatVersion;

    @Option(key = "tomcat.service", description = "Java Service Wrapper version to use around Tomcat.")
    public String tomcatService;

    @Option(key = "tomcat.heap", description = "memory in mb")
    public Integer tomcatHeap;

    @Option(key = "tomcat.perm", description = "memory in mb")
    public Integer tomcatPerm;

    @Option(key = "tomcat.select", description = "hostnames to start - empty for all")
    public List<String> tomcatSelect;

    @Option(key = "tomcat.env", description = "Environment variables passes to applications. The current envionment is "
            + "intentionally unavailable for the running application; this is to make things reproducible.")
    public Map<String, String> tomcatEnv;

    @Option(key = "java.home", description = "jdk or jre directory")
    public String javaHome;

    // never null
    @Option(key = "maven.home", description = "Maven home")
    private String mavenHome;

    @Option(key = "maven.opts",
      description = "MAVEN_OPTS when building this stage. Supported variables: @trustStore@, @proxyOpts@ and @localRepository@")
    public String mavenOpts;

    @Option(key = "until", description = "YYYY-MM-DD")
    public Until until;

    @Option(key = "suffix", description = "suffix for the link eg. http://1and1.com/{suffix}")
    public String suffix;

    @Option(key = "sslUrl", description = "overrides the default url for certificate creation")
    public String sslUrl;

    @Option(key = "autoRefresh", description = "true if a stage should care about refreshing by itself")
    public Boolean autoRefresh;

    @Option(key = "comment", description = "a comment")
    public String comment;

    public final Extensions extensions;

    public StageConfiguration(String id, String javaHome, String mavenHome, Extensions extensions) {
        this.id = id;
        this.cookies = true;
        this.prepare = "";
        this.build = "false";
        this.refresh = "svn @svnCredentials@ up";
        this.pom = "pom.xml";
        this.tomcatOpts = "";
        this.tomcatVersion = "8.0.26";
        this.tomcatService = "3.5.26";
        this.tomcatHeap = 200;
        this.tomcatPerm = 64;
        this.tomcatSelect = new ArrayList<>();
        this.tomcatEnv = new HashMap<>();
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

    public void save(Gson gson, Node file) throws IOException {
        try (Writer writer = file.createWriter()) {
            gson.toJson(this, writer);
        }
    }


    public String mavenHome() {
        return mavenHome.isEmpty() ? null : mavenHome;
    }
}

