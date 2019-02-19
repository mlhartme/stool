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

import net.oneandone.stool.docker.Engine;

import java.io.IOException;
import java.time.LocalDateTime;

public class Image implements Comparable<Image> {
    public static Image load(Engine engine, String id) throws IOException {
        LocalDateTime created;

        created = LocalDateTime.parse(engine.imageInspect(id).get("Created").getAsString(), Engine.CREATED_FMT);
        return new Image(id, created);
    }

    public final String id;
    public final LocalDateTime created;

    public Image(String id, LocalDateTime created) {
        this.id = id;
        this.created = created;
    }

    @Override
    public int compareTo(Image o) {
        return created.compareTo(o.created);
    }

    public String toString() {
        return created.toString();
    }
}
