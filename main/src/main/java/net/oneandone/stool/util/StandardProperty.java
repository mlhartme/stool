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

import net.oneandone.stool.server.configuration.Accessor;
import net.oneandone.stool.server.configuration.StageConfiguration;

/** A computable value representing one aspect of the stage status. */
public class StandardProperty extends Property {
    private final Accessor type;
    private final StageConfiguration configuration;

    public StandardProperty(Accessor type, StageConfiguration configuration) {
        super(type.name);
        this.type = type;
        this.configuration = configuration;
    }

    public String get() {
        return type.get(configuration);
    }

    public void set(String str) {
        type.set(configuration, str);
    }
}
