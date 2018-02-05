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
package net.oneandone.stool.configuration.adapter;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.Streams;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.oneandone.stool.templates.Template;
import net.oneandone.stool.templates.Templates;
import net.oneandone.stool.templates.TemplatesFactory;
import net.oneandone.stool.templates.Switch;

import java.io.IOException;
import java.util.Map;

public class TemplatesAdapter extends TypeAdapter<Templates> {
    public static TypeAdapterFactory factory(final TemplatesFactory factory) {
        return new TypeAdapterFactory() {
            @SuppressWarnings("unchecked")
            public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                if (type.getRawType().equals(Templates.class)) {
                    return (TypeAdapter<T>) new TemplatesAdapter(gson, factory);
                }
                return null;
            }
        };
    }

    private final Gson gson;
    private final TemplatesFactory factory;

    public TemplatesAdapter(Gson gson, TemplatesFactory factory) {
        this.gson = gson;
        this.factory = factory;
    }

    @Override
    public void write(JsonWriter out, Templates value) throws IOException {
        Switch s;

        out.beginObject();
        for (Map.Entry<String, Switch> entry : value.templates.entrySet()) {
            s = entry.getValue();
            out.name(s.marker() + entry.getKey());
            Streams.write(gson.toJsonTree(s.template), out);
        }
        out.endObject();
    }

    @Override
    public Templates read(JsonReader in) throws IOException {
        Templates templates;
        String str;
        String name;
        Template template;
        Class<? extends Template> clazz;

        templates = new Templates();
        in.beginObject();
        while (in.peek() == JsonToken.NAME) {
            str = in.nextName();
            name = str.substring(1);
            clazz = factory.type(name);
            if (clazz == null) {
                throw new IOException("template not found: " + name);
            }
            template = gson.fromJson(in, clazz);
            templates.add(name, str.startsWith("+"), template);
        }
        in.endObject();
        return templates;
    }
}
