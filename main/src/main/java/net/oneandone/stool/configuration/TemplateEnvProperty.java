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
package net.oneandone.stool.configuration;

/** Handles Stool or Stage property. Converts between strings an objects and deals with reflection */
public class TemplateEnvProperty extends Property {
    private static final String PREFIX = "template.";

    private final String subname;

    public TemplateEnvProperty(String name, String subname) {
        super(name);
        this.subname = subname;
    }

    protected String doGet(Object configuration) {
        String result;

        result = ((StageConfiguration) configuration).templateEnv.get(subname);
        return result == null ? "" : result;
    }

    protected void doSet(Object configuration, String value) {
        StageConfiguration config;

        config = (StageConfiguration) configuration;
        config.templateEnv.put(subname, value);
    }

    public static Property createOpt(String name) {
        if (!name.startsWith(PREFIX)) {
            return null;
        }
        return new TemplateEnvProperty(name, name.substring(PREFIX.length()));
    }
}
