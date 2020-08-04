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
import net.oneandone.stool.kubernetes.DataInfo;
import net.oneandone.stool.kubernetes.Engine;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StageConfiguration {
    public static final String NOTIFY_CREATED_BY = "@created-by";
    public static final String NOTIFY_LAST_MODIFIED_BY = "@last-modified-by";

    private static final String CONFIG_MAP_KEY = "config";

    public static StageConfiguration load(Gson gson, Engine engine, String stageName) throws IOException {
        String str;
        StringReader reader;

        str = engine.configMapRead(stageName).get(CONFIG_MAP_KEY);
        reader = new StringReader(str);
        return gson.fromJson(reader, StageConfiguration.class);
    }

    public static final List<String> list(Engine engine) throws IOException {
        List<String> result;

        result = new ArrayList<>();
        for (DataInfo info : engine.configMapList().values()) { // TODO: use special label to mark stage configs
            if (engine.hasImplicit(info.labels)) {
                result.add(info.name);
            }
        }
        return result;
    }

    //--

    /** login names or email addresses, or "@last-modified-by" or "@created-by" */
    @Option(key = "notify")
    public List<String> notify;

    @Option(key = "expire")
    public Expire expire;

    @Option(key = "comment")
    public String comment;

    @Option(key = "environment")
    public Map<String, String> environment;

    public StageConfiguration() {
        this.notify = new ArrayList<>();
        this.notify.add(NOTIFY_CREATED_BY);
        this.expire = Expire.never();
        this.comment = "";
        this.environment = new HashMap<>();
    }

    public void save(Gson gson, Engine engine, String stageName, boolean overwrite) throws IOException {
        StringWriter writer;
        Map<String, String> map;

        if (overwrite) {
            delete(engine, stageName);
        }
        writer = new StringWriter();
        gson.toJson(this, writer);
        map = new HashMap<>();
        map.put(CONFIG_MAP_KEY, writer.toString());
        engine.configMapCreate(stageName, map);
    }

    public static void delete(Engine engine, String stageName) throws IOException {
        engine.configMapDelete(stageName);
    }

    //--

    /** you'll usually invoke server.accessors() instead */
    public static Map<String, Accessor> accessors() {
        Map<String, Accessor> result;
        Option option;

        result = new LinkedHashMap<>();
        for (java.lang.reflect.Field field : StageConfiguration.class.getFields()) {
            option = field.getAnnotation(Option.class);
            if (option != null) {
                result.put(option.key(), new ReflectAccessor(option.key(), field));
            }
        }
        return result;
    }
}

