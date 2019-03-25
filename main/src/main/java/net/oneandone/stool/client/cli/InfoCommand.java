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
package net.oneandone.stool.client.cli;

import net.oneandone.stool.util.Server;
import net.oneandone.sushi.util.Separator;

import java.util.ArrayList;
import java.util.List;

public abstract class InfoCommand extends StageCommand {

    protected final List<String> selected = new ArrayList<>();

    private final String defaults;

    public InfoCommand(Server server, String defaults) {
        super(server);
        this.defaults = defaults == null ? "" : defaults;
    }

    public void select(String str) {
        selected.add(str);
    }

    protected List<String> defaults() {
        return Separator.COMMA.split(defaults);
    }
}
