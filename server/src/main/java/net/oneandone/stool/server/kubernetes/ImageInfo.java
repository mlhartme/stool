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
package net.oneandone.stool.server.kubernetes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ImageInfo {
    public final String id;
    public final LocalDateTime created;
    public final List<String> repositoryTags;
    public final Map<String, String> labels;

    public ImageInfo(String id, List<String> repositoryTags, LocalDateTime created, Map<String, String> labels) {
        this.id = id;
        this.created = created;
        this.repositoryTags = repositoryTags;
        this.labels = labels;
    }
}
