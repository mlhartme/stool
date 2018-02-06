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
package net.oneandone.stool.templates;

import java.lang.reflect.Field;

public class Switch {
    public static final Field FIELD;

    static {
        try {
            FIELD = Switch.class.getDeclaredField("enabled");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    //--

    public boolean enabled;
    public final Template template;

    public Switch(boolean enabled, Template template) {
        if (template == null) {
            throw new IllegalStateException();
        }
        this.enabled = enabled;
        this.template = template;
    }

    public String marker() {
        return enabled ? "+" : "-";
    }
}
