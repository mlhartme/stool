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
package net.oneandone.stool.core;

import net.oneandone.stool.kubernetes.Engine;

import java.io.IOException;

/** A computable value representing one aspect of the stage status. */
public abstract class Field extends Info {
    public final boolean hidden;

    protected Field(String name) {
        this(name, false);
    }

    protected Field(String name, boolean hidden) {
        super(name);
        this.hidden = hidden;
    }

    public String toString() {
        return name();
    }

    public abstract Object get(Engine engine) throws IOException;
}
