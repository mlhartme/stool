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
package net.oneandone.stool.server.docker;

import com.google.gson.JsonObject;

import java.io.IOException;

public class BuildError extends IOException {
    public final String nameAndTag;
    public final String error;
    public final JsonObject details;
    public final String output;

    public BuildError(String nameAndTag, String error, JsonObject details, String output) {
        super("docker build failed: " + error);
        this.nameAndTag = nameAndTag;
        this.error = error;
        this.details = details;
        this.output = output;
    }
}
