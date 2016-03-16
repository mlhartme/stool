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
    public static FileNode file(FileNode backstage) {
        return backstage.join("config.json");
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
                result.put(option.key(), new Property(option.key(), field, null));
            }
        }
        extensions.fields(result);
        return result;
    }

    //--

    @Option(key = "id", readOnly = true)
    public final String id;

    @Option(key = "cookies")
    public Boolean cookies;

    @Option(key = "prepare")
    public String prepare;

    @Option(key = "build")
    public String build;

    @Option(key = "refresh")
    public String refresh;

    @Option(key = "pom")
    public String pom;

    @Option(key = "tomcat.opts")
    public String tomcatOpts;

    @Option(key = "tomcat.version")
    public String tomcatVersion;

    @Option(key = "tomcat.service")
    public String tomcatService;

    @Option(key = "tomcat.heap")
    public Integer tomcatHeap;

    @Option(key = "tomcat.perm")
    public Integer tomcatPerm;

    @Option(key = "tomcat.select")
    public List<String> tomcatSelect;

    @Option(key = "tomcat.env")
    public Map<String, String> tomcatEnv;

    @Option(key = "java.home")
    public String javaHome;

    // never null
    @Option(key = "maven.home")
    private String mavenHome;

    @Option(key = "maven.opts")
    public String mavenOpts;

    @Option(key = "until")
    public Until until;

    @Option(key = "suffixes")
    public List<String> suffixes;

    @Option(key = "sslUrl")
    public String sslUrl;

    @Option(key = "autoRefresh")
    public Boolean autoRefresh;

    @Option(key = "comment")
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
        this.tomcatVersion = "8.0.32";
        this.tomcatService = "3.5.29";
        this.tomcatHeap = 200;
        this.tomcatPerm = 64;
        this.tomcatSelect = new ArrayList<>();
        this.tomcatEnv = new HashMap<>();
        this.javaHome = javaHome;
        this.mavenHome = mavenHome;
        this.mavenOpts = "";
        this.until = Until.reserved();
        this.suffixes = new ArrayList<>();
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

