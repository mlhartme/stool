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
package net.oneandone.stool.util;

import net.oneandone.stool.stage.Stage;

import java.util.List;
import java.util.ArrayList;

/** The computable value representing an aspect of the stage status. That this enum is only the status part, there are also dynamic fields defined in the template */
public class Field implements Info {
    public static final Field ID = new Field("id");
    public static final Field SELECTED = new Field("selected");
    public static final Field DIRECTORY = new Field("directory");
    public static final Field BACKSTAGE = new Field("backstage");
    public static final Field URL = new Field("url");
    public static final Field TYPE = new Field("type");
    public static final Field CREATOR = new Field("creator");
    public static final Field CREATED = new Field("created");
    public static final Field BUILDTIME = new Field("buildtime");
    public static final Field LAST_MODIFIED_BY = new Field("last-modified-by");
    public static final Field LAST_MODIFIED_AT = new Field("last-modified-at");
    public static final Field DISK = new Field("disk");
    public static final Field STATE = new Field("state");
    public static final Field UPTIME = new Field("uptime");
    public static final Field CPU = new Field("cpu");
    public static final Field MEM = new Field("mem");
    public static final Field CONTAINER = new Field("container");
    public static final Field DEBUGGER = new Field("debugger");
    public static final Field SUSPEND = new Field("suspend");
    public static final Field APPS = new Field("apps");
    public static final Field OTHER = new Field("other");

    // TODO
    public static List<Field> values() {
        List<Field> fields;

        fields = new ArrayList<>();
        fields.add(ID);
        fields.add(SELECTED);
        fields.add(DIRECTORY);
        fields.add(BACKSTAGE);
        fields.add(URL);
        fields.add(TYPE);
        fields.add(CREATOR);
        fields.add(CREATED);
        fields.add(BUILDTIME);
        fields.add(LAST_MODIFIED_BY);
        fields.add(LAST_MODIFIED_AT);
        fields.add(DISK);
        fields.add(STATE);
        fields.add(UPTIME);
        fields.add(CPU);
        fields.add(MEM);
        fields.add(CONTAINER);
        fields.add(DEBUGGER);
        fields.add(SUSPEND);
        fields.add(APPS);
        fields.add(OTHER);
        return fields;
    }

    // TODO
    public static Field valueOfOpt(String str) {
        for (Field f : values()) {
            if (str.equals(f.name)) {
                return f;
            }
        }
        return null;
    }

    //--

    public final String name;

    private Field(String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }

    public String infoName() {
        return name;
    }
}
