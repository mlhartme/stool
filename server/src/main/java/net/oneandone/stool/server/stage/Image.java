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
package net.oneandone.stool.server.stage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.util.Ports;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Image implements Comparable<Image> {
    public static Image load(Engine engine, String repositoryTag) throws IOException {
        JsonObject inspect;
        JsonObject labels;
        LocalDateTime created;
        String app;

        inspect = engine.imageInspect(repositoryTag);
        created = LocalDateTime.parse(inspect.get("Created").getAsString(), Engine.CREATED_FMT);
        labels = inspect.get("Config").getAsJsonObject().get("Labels").getAsJsonObject();
        app = app(repositoryTag);
        return new Image(repositoryTag, version(repositoryTag), created, Ports.fromDeclaredLabels(labels),
                p12(labels.get(Stage.IMAGE_LABEL_P12)),
                app,
                disk(labels.get(Stage.IMAGE_LABEL_DISK)),
                memory(labels.get(Stage.IMAGE_LABEL_MEMORY)),
                context(labels.get(Stage.IMAGE_LABEL_URL_CONTEXT)),
                suffixes(labels.get(Stage.IMAGE_LABEL_URL_SUFFIXES)),
                labels.get(Stage.IMAGE_LABEL_COMMENT).getAsString(),
                labels.get(Stage.IMAGE_LABEL_ORIGIN_SCM).getAsString(),
                labels.get(Stage.IMAGE_LABEL_CREATED_BY).getAsString(),
                labels.get(Stage.IMAGE_LABEL_CREATED_ON).getAsString(),
                args(labels),
                fault(labels.get(Stage.IMAGE_LABEL_FAULT)));
    }

    private static Map<String, String> args(JsonObject labels) {
        Map<String, String> result;
        String key;

        result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : labels.entrySet()) {
            key = entry.getKey();
            if (key.startsWith(Stage.IMAGE_LABEL_ARG_PREFIX)) {
                result.put(key.substring(Stage.IMAGE_LABEL_ARG_PREFIX.length()), entry.getValue().getAsString());
            }
        }
        return result;
    }

    private static String p12(JsonElement element) {
        return element == null ? null : element.getAsString();
    }

    private static int memory(JsonElement element) {
        return element == null ? 1024 : Integer.parseInt(element.getAsString());
    }

    private static int disk(JsonElement element) {
        return element == null ? 1024 * 42 : Integer.parseInt(element.getAsString());
    }

    private static String context(JsonElement element) {
        String result;

        if (element == null) {
            return "";
        }
        result = element.getAsString();
        if (result.startsWith("/")) {
            throw new ArithmeticException("server must not start with '/': " + result);
        }
        if (!result.isEmpty() && result.endsWith("/")) {
                throw new ArithmeticException("server must not end with '/': " + result);
        }
        return result;
    }

    private static final Separator SUFFIXES_SEP = Separator.on(',').trim();

    private static List<String> suffixes(JsonElement element) {
        List<String> result;

        result = new ArrayList<>();
        if (element != null) {
            result.addAll(SUFFIXES_SEP.split(element.getAsString()));
        }
        if (result.isEmpty()) {
            result.add("");
        }
        return result;
    }

    private static List<String> fault(JsonElement element) {
        List<String> result;

        result = new ArrayList<>();
        if (element != null) {
            result.addAll(Separator.COMMA.split(element.getAsString()));
        }
        return result;
    }

    public static String app(String repositoryTag) {
        String result;
        int idx;

        result = repositoryTag;
        idx = result.indexOf(':');
        if (idx == -1) {
            throw new IllegalStateException(result);
        }
        result = result.substring(0, idx);
        idx = result.lastIndexOf('/');
        if (idx == -1) {
            throw new IllegalStateException(result);
        }
        return result.substring(idx + 1);
    }

    public static String version(String repositoryTag) {
        String result;
        int idx;

        result = repositoryTag;
        idx = result.lastIndexOf(':');
        if (idx == -1) {
            throw new IllegalStateException(result);
        }
        return result.substring(idx + 1);
    }

    public final String repositoryTag;
    public final String tag;
    /** parsed version, null if version is not a number */
    public final Integer tagNumber;
    public final LocalDateTime createdAt;

    //-- meta data

    public final Ports ports;

    public final String p12;

    public final String app;

    /** in megabytes */
    public final int disk;

    /** memory in megabytes */
    public final int memory;

    public final String urlContext;
    public final List<String> urlSuffixes;

    /** docker api returns a comment field, but i didn't find documentation how to set it */
    public final String comment;
    public final String originScm;
    public final String createdBy;
    public final String createdOn;
    public final Map<String, String> args;

    /** maps relative host path to absolute container path */
    public final List<String> faultProjects;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public Image(String repositoryTag, String tag, LocalDateTime createdAt, Ports ports, String p12, String app, int disk, int memory, String urlContext, List<String> urlSuffixes,
                 String comment, String originScm, String createdBy, String createdOn, Map<String, String> args, List<String> faultProjects) {
        if (!urlContext.isEmpty()) {
            if (urlContext.startsWith("/") || urlContext.endsWith("/")) {
                throw new IllegalArgumentException(urlContext);
            }
        }
        this.repositoryTag = repositoryTag;
        this.tag = tag;
        this.tagNumber = parseOpt(tag);
        this.createdAt = createdAt;

        this.ports = ports;
        this.p12 = p12;
        this.app = app;
        this.disk = disk;
        this.memory = memory;
        this.urlContext = urlContext;
        this.urlSuffixes = urlSuffixes;
        this.comment = comment;
        this.originScm = originScm;
        this.createdBy = createdBy;
        this.createdOn = createdOn;
        this.args = args;
        this.faultProjects = faultProjects;
    }

    private static Integer parseOpt(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public int compareTo(Image o) {
        if (tagNumber != null && o.tagNumber != null) {
            return tagNumber.compareTo(o.tagNumber);
        } else if (tagNumber == null && o.tagNumber == null) {
            return tag.compareTo(o.tag);
        } else {
            return tagNumber != null ? -1 : 1;
        }
    }

    public String toString() {
        return repositoryTag + " " + createdAt.toString();
    }

    //--

    public static int nextTag(List<Image> images) {
        Image image;

        for (int i = images.size() - 1; i >= 0; i--) {
            image = images.get(i);
            if (image.tagNumber != null) {
                return image.tagNumber + 1;
            }
        }
        return 1;
    }
}
