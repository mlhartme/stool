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
package net.oneandone.stool.stage;

import com.google.gson.JsonObject;
import net.oneandone.stool.docker.Engine;
import net.oneandone.sushi.util.Separator;

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
        return new Image(id, created,
                portsFromString(labels.get(Stage.LABEL_PORTS).getAsString()),
                labels.get(Stage.LABEL_COMMENT).getAsString(),
                labels.get(Stage.LABEL_ORIGIN).getAsString(),
                labels.get(Stage.LABEL_CREATED_BY).getAsString(),
                labels.get(Stage.LABEL_CREATED_ON).getAsString());
    }

    private static Map<Integer, Integer> portsFromString(String str) {
        String key;
        Map<Integer, Integer> result;

        key = null;
        result = new HashMap<>();
        for (String entry : Separator.COMMA.split(str)) {
            if (key == null) {
                key = entry;
            } else {
                result.put(Integer.parseInt(key), Integer.parseInt(entry));
                key = null;
            }
        }
        return result;
    }

    public final String id;
    public final LocalDateTime created;

    //-- meta data

    /** docker api returns an author field, but i didn't find documentation how to set it */
    public final Map<Integer, Integer> ports;

    /** docker api returns a comment field, but i didn't find documentation how to set it */
    public final String comment;
    public final String origin;
    public final String createdBy;
    public final String createdOn;

    public Image(String id, LocalDateTime created, Map<Integer, Integer> ports, String comment, String origin, String createdBy, String createdOn) {
        this.id = id;
        this.created = created;

        this.ports = ports;
        this.comment = comment;
        this.origin = origin;
        this.createdBy = createdBy;
        this.createdOn = createdOn;
    }

    @Override
    public int compareTo(Image o) {
        return created.compareTo(o.created);
    }

    public String toString() {
        return created.toString();
    }
}
