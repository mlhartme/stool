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
package net.oneandone.stool.configuration;

import com.google.gson.Gson;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StageConfiguration {
    public static final String NOTIFY_CREATED_BY = "@created-by";
    public static final String NOTIFY_LAST_MODIFIED_BY = "@last-modified-by";

    public static FileNode file(FileNode backstage) {
        return backstage.join("config.json");
    }

    public static StageConfiguration load(Gson gson, Node file) throws IOException {
        try (Reader reader = file.newReader()) {
            return gson.fromJson(reader, StageConfiguration.class);
        }
    }

    //--

    @Option(key = "name")
    public String name;

    @Option(key = "quota")
    public int quota;

    /** max ram for container, in MB */
    @Option(key = "memory")
    public Integer memory;

    @Option(key = "select")
    public List<String> select;

    /** login names or email addresses, or "@last-modified-by" or "@created-by" */
    @Option(key = "notify")
    public List<String> notify;

    @Option(key = "expire")
    public Expire expire;

    @Option(key = "url")
    public String url;

    @Option(key = "autoRefresh")
    public Boolean autoRefresh;

    @Option(key = "comment")
    public String comment;

    @Option(key = "template")
    public FileNode template;

    @Option(key = "template.env")
    public Map<String, String> templateEnv;

    public StageConfiguration(FileNode template) {
        this.name = "noname";
        this.notify = new ArrayList<>();
        this.notify.add(NOTIFY_CREATED_BY);
        this.quota = 10000;
        this.memory = 400;
        this.select = new ArrayList<>();
        this.expire = Expire.never();
        this.url = "(http:https)://%h/";
        this.comment = "";
        this.autoRefresh = false;
        this.template = template;
        // TODO: duplicates Dockerfile template defaults
        this.templateEnv = Strings.toMap("version", "9.0.13", "opts", "", "debug", "false", "suspend", "false");
    }

    public void save(Gson gson, Node file) throws IOException {
        try (Writer writer = file.newWriter()) {
            gson.toJson(this, writer);
        }
    }

    //--

    /** you'll usually invoke session.accessors() instead */
    public static Map<String,Accessor> accessors(FileNode templates) {
        Map<String, Accessor> result;
        Option option;

        result = new LinkedHashMap<>();
        for (java.lang.reflect.Field field : StageConfiguration.class.getFields()) {
            option = field.getAnnotation(Option.class);
            if (option != null) {
                if (option.key().equals("template")) {
                    result.put(option.key(), new TemplateAccessor(option.key(), templates));
                } else {
                    result.put(option.key(), new ReflectAccessor(option.key(), field));
                }
            }
        }
        return result;
    }
}

