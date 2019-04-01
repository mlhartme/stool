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

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class Image implements Comparable<Image> {
    public static Image load(Engine engine, String id) throws IOException {
        JsonObject inspect;
        JsonObject labels;
        LocalDateTime created;

        inspect = engine.imageInspect(id);
        created = LocalDateTime.parse(inspect.get("Created").getAsString(), Engine.CREATED_FMT);
        labels = inspect.get("Config").getAsJsonObject().get("Labels").getAsJsonObject();
        return new Image(id, created, Ports.fromContainerLabels(labels),
                labels.get(Stage.IMAGE_LABEL_APP).getAsString(),
                labels.get(Stage.IMAGE_LABEL_COMMENT).getAsString(),
                labels.get(Stage.IMAGE_LABEL_ORIGIN).getAsString(),
                labels.get(Stage.IMAGE_LABEL_CREATED_BY).getAsString(),
                labels.get(Stage.IMAGE_LABEL_CREATED_ON).getAsString(),
                secrets(labels));
    }

    private static Map<String, String> secrets(JsonObject labels) {
        Map<String, String> secrets;
        String key;

        secrets = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : labels.entrySet()) {
            key = entry.getKey();
            if (key.startsWith(Stage.LABEL_MOUNT_SECRETS_PREFIX)) {
                secrets.put(key.substring(Stage.LABEL_MOUNT_SECRETS_PREFIX.length()), entry.getValue().getAsString());
            }

        }
        return secrets;
    }

    public final String id;
    public final LocalDateTime created;

    //-- meta data

    public final Ports ports;

    public final String app;

    /** docker api returns a comment field, but i didn't find documentation how to set it */
    public final String comment;
    public final String origin;
    public final String createdBy;
    public final String createdOn;

    /** maps relative host path to absolute container path */
    public final Map<String, String> secrets;

    public Image(String id, LocalDateTime created, Ports ports, String app, String comment, String origin, String createdBy, String createdOn,
                 Map<String, String> secrets) {
        this.id = id;
        this.created = created;

        this.ports = ports;
        this.app = app;
        this.comment = comment;
        this.origin = origin;
        this.createdBy = createdBy;
        this.createdOn = createdOn;
        this.secrets = secrets;
    }

    @Override
    public int compareTo(Image o) {
        return -created.compareTo(o.created);
    }

    public String toString() {
        return created.toString();
    }
}
