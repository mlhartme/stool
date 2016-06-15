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
import net.oneandone.stool.extensions.Switch;

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
        Switch s;

        out.beginObject();
        for (Map.Entry<String, Switch> entry : value.extensions.entrySet()) {
            s = entry.getValue();
            out.name(s.marker() + entry.getKey());
            Streams.write(gson.toJsonTree(s.extension), out);
        }
        out.endObject();
    }

    @Override
    public Extensions read(JsonReader in) throws IOException {
        Extensions extensions;
        String str;
        String name;
        Extension extension;
        Class<? extends Extension> clazz;

        extensions = new Extensions();
        in.beginObject();
        while (in.peek() == JsonToken.NAME) {
            str = in.nextName();
            name = str.substring(1);
            clazz = factory.type(name);
            if (clazz == null) {
                throw new IOException("extension not found: " + name);
            }
            extension = gson.fromJson(in, clazz);
            extensions.add(name, str.startsWith("+"), extension);
        }
        in.endObject();
        return extensions;
    }
}
