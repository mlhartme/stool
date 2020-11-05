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

import net.oneandone.stool.kubernetes.Engine;
import net.oneandone.stool.server.ArgumentException;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StageConfiguration {
    public static final String NOTIFY_CREATED_BY = "@created-by";
    public static final String NOTIFY_LAST_MODIFIED_BY = "@last-modified-by";

    private static final String EXPIRE = "stageExpire";
    private static final String COMMENT = "stageComment";
    private static final String REPOSITORY = "stageRepository";
    private static final String NOTIFY = "stageNotify";

    public static StageConfiguration load(Engine engine, String stageName) throws IOException {
        Map<String, Object> values;
        StageConfiguration result;
        String str;

        values = engine.helmReadValues(stageName);
        result = new StageConfiguration((String) values.get(REPOSITORY));
        result.notify = Separator.COMMA.split(opt((String) values.get(NOTIFY)));
        str = (String) values.get(EXPIRE);
        result.expire = str == null ? Expire.never() : Expire.fromHuman(str);  //TODO: really never?
        result.comment = opt((String) values.get(COMMENT));
        return result;
    }

    private static String opt(String str) {
        return str == null ? "" : str;
    }

    public static final List<String> list(Engine engine) throws IOException {
        List<String> result;

        result = engine.helmList();
        if (!result.remove("stool")) {
            throw new IllegalStateException(result.toString());
        }
        return result;
    }

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

    public void save(Map<String, Object> dest) {
        dest.put(REPOSITORY, repository);
        dest.put(NOTIFY, Separator.COMMA.join(notify));
        dest.put(EXPIRE, expire.toString());
        dest.put(COMMENT, comment);
    }
}

