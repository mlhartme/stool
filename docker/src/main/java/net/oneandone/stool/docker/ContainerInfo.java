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
package net.oneandone.stool.docker;

import com.google.gson.JsonObject;

public class ContainerInfo {
    public static ContainerInfo create(JsonObject object) {
        String id;
        String imageId;
        Docker.Status state; // TODO: sometimes it's called Status, sometimes state ...

        id = object.get("Id").getAsString();
        imageId = Docker.pruneImageId(object.get("ImageID").getAsString());
        state = Docker.Status.valueOf(object.get("State").getAsString().toUpperCase());
        return new ContainerInfo(id, imageId, state);
    }
    public final String id;
    public final String imageId;
    public final Docker.Status state;

    public ContainerInfo(String id, String imageId, Docker.Status state) {
        this.id = id;
        this.imageId = imageId;
        this.state = state;
    }
}
