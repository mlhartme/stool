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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.util.Ports;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Image implements Comparable<Image> {
    public static Image load(Engine engine, String id) throws IOException {
        JsonObject inspect;
        JsonObject labels;
        LocalDateTime created;
        String app;

        inspect = engine.imageInspect(id);
        created = LocalDateTime.parse(inspect.get("Created").getAsString(), Engine.CREATED_FMT);
        labels = inspect.get("Config").getAsJsonObject().get("Labels").getAsJsonObject();
        app = app(inspect.get("RepoTags").getAsJsonArray());
        return new Image(id, created, Ports.fromDeclaredLabels(labels),
                app,
                memory(labels.get(Stage.IMAGE_LABEL_MEMORY)),
                context(labels.get(Stage.IMAGE_LABEL_URL_CONTEXT)),
                suffixes(labels.get(Stage.IMAGE_LABEL_URL_SUFFIXES)),
                labels.get(Stage.IMAGE_LABEL_COMMENT).getAsString(),
                labels.get(Stage.IMAGE_LABEL_ORIGIN).getAsString(),
                labels.get(Stage.IMAGE_LABEL_CREATED_BY).getAsString(),
                labels.get(Stage.IMAGE_LABEL_CREATED_ON).getAsString(),
                fault(labels.get(Stage.IMAGE_LABEL_FAULT)));
    }

    private static int memory(JsonElement element) {
        return element == null ? 1024 : Integer.parseInt(element.getAsString());
    }

    private static String context(JsonElement element) {
        String result;

        if (element == null) {
            return "";
        }
        result = element.getAsString();
        if (result.startsWith("/")) {
            throw new ArithmeticException("context must not start with '/': " + result);
        }
        if (result.isEmpty() || !result.endsWith("/")) {
            throw new ArithmeticException("context has to end with '/': " + result);
        }
        return result;
    }

    private static List<String> suffixes(JsonElement element) {
        List<String> result;

        result = new ArrayList<>();
        if (element != null) {
            result.addAll(Separator.COMMA.split(element.getAsString()));
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

    private static String app(JsonArray array) {
        String result;
        int idx;

        if (array.size() != 1) {
            throw new IllegalStateException(array.toString());
        }
        result = array.get(0).getAsString();
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

    public final String id;
    public final LocalDateTime created;

    //-- meta data

    public final Ports ports;

    public final String app;

    /** memory in megabytes */
    public final int memory;

    public final String urlContext;
    public final List<String> urlSuffixes;

    /** docker api returns a comment field, but i didn't find documentation how to set it */
    public final String comment;
    public final String origin;
    public final String createdBy;
    public final String createdOn;

    /** maps relative host path to absolute container path */
    public final List<String> faultProjects;

    public Image(String id, LocalDateTime created, Ports ports, String app, int memory, String urlContext, List<String> urlSuffixes,
                 String comment, String origin, String createdBy, String createdOn, List<String> faultProjects) {
        this.id = id;
        this.created = created;

        this.ports = ports;
        this.app = app;
        this.memory = memory;
        this.urlContext = urlContext;
        this.urlSuffixes = urlSuffixes;
        this.comment = comment;
        this.origin = origin;
        this.createdBy = createdBy;
        this.createdOn = createdOn;
        this.faultProjects = faultProjects;
    }

    @Override
    public int compareTo(Image o) {
        return -created.compareTo(o.created);
    }

    public String toString() {
        return created.toString();
    }
}
