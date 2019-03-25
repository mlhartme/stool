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
package net.oneandone.stool.net.oneandone.stool.server.configuration.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.oneandone.stool.net.oneandone.stool.server.configuration.Expire;

import java.io.IOException;

public class ExpireTypeAdapter extends TypeAdapter<Expire> {
    @Override
    public void write(JsonWriter out, Expire value) throws IOException {
        out.value(value.toString());
    }

    @Override
    public Expire read(JsonReader in) throws IOException {
        return Expire.fromString(in.nextString());
    }
}
