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
import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.server.ArgumentException;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
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
        return new ArrayList<>(engine.configMapList(CONFIG_MARKER).keySet());
    }

    private static final Map<String, String> CONFIG_MARKER = Collections.unmodifiableMap(Strings.toMap("type", "stage-config"));

    //--

    @Option(key = "repository")
    public String repository;

    /** login names or email addresses, or "@last-modified-by" or "@created-by" */
    @Option(key = "notify")
    public List<String> notify;

    @Option(key = "expire")
    public Expire expire;

    @Option(key = "comment")
    public String comment;

    public StageConfiguration(String repository) {
        this.repository = repository;
        this.notify = new ArrayList<>();
        this.notify.add(NOTIFY_CREATED_BY);
        this.expire = Expire.never();
        this.comment = "";
    }

    //--

    /** @return full repository url, i.e. with server and path */
    public String repository() {
        return repository;
    }

    // this is to avoid engine 500 error reporting "invalid reference format: repository name must be lowercase"
    public static void validateRepository(String repository) {
        URI uri;

        if (repository.endsWith("/")) {
            throw new ArgumentException("invalid repository: " + repository);
        }
        try {
            uri = new URI(repository);
        } catch (URISyntaxException e) {
            throw new ArgumentException("invalid repository: " + repository);
        }
        if (uri.getHost() != null) {
            checkLowercase(uri.getHost());
        }
        checkLowercase(uri.getPath());
    }

    private static void checkLowercase(String str) {
        for (int i = 0, length = str.length(); i < length; i++) {
            if (Character.isUpperCase(str.charAt(i))) {
                throw new ArgumentException("invalid registry prefix: " + str);
            }
        }
    }


    //--
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
        engine.configMapCreate(stageName, map, CONFIG_MARKER);
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

