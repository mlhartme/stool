/**
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
package net.oneandone.stool.configuration.adapter;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.Streams;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.oneandone.stool.extensions.Extension;
import net.oneandone.stool.extensions.Extensions;
import net.oneandone.stool.extensions.ExtensionsFactory;

import java.io.IOException;
import java.util.Map;

public class ExtensionsAdapter extends TypeAdapter<Extensions> {
    public static TypeAdapterFactory factory(final ExtensionsFactory factory) {
        return new TypeAdapterFactory() {
            @SuppressWarnings("unchecked")
            public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                if (type.getRawType().equals(Extensions.class)) {
                    return (TypeAdapter<T>) new ExtensionsAdapter(gson, factory);
                }
                return null;
            }
        };
    }

    private final Gson gson;
    private final ExtensionsFactory factory;

    public ExtensionsAdapter(Gson gson, ExtensionsFactory factory) {
        this.gson = gson;
        this.factory = factory;
    }
    @Override
    public void write(JsonWriter out, Extensions value) throws IOException {
        out.beginObject();
        for (Map.Entry<String, Extension> entry : value.extensions.entrySet()) {
            out.name(entry.getKey());
            Streams.write(gson.toJsonTree(entry.getValue()), out);
        }
        out.endObject();
    }

    @Override
    public Extensions read(JsonReader in) throws IOException {
        Extensions extensions;
        String name;
        Extension extension;

        extensions = new Extensions();
        in.beginObject();
        while (in.peek() == JsonToken.NAME) {
            name = in.nextName();
            extension = gson.fromJson(in, factory.type(name));
            extensions.add(name, extension);
        }
        in.endObject();
        return extensions;
    }
}
