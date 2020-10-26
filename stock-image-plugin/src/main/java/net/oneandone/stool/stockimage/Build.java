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
package net.oneandone.stool.stockimage;

import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.stool.docker.Daemon;
import net.oneandone.sushi.fs.World;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Build {
    private final World world;
    private final Console console;
    private final String explicitApp;
    private final boolean noCache;
    private final int keep;
    private final String comment;
    private final Map<String, String> explicitArguments;

    public Build(World world, String explicitApp, boolean noCache, int keep, String comment, List<String> args) {
        this.world = world;
        this.console = Console.create();
        this.explicitApp = explicitApp;
        this.noCache = noCache;
        this.keep = keep;
        this.comment = comment;
        this.explicitArguments = argument(args);
    }

    private static Map<String, String> argument(List<String> args) {
        int idx;
        Map<String, String> result;

        result = new HashMap<>();
        for (String arg : args) {
            idx = arg.indexOf('=');
            if (idx == -1) {
                throw new ArgumentException("invalid argument: <key>=<value> expected, got " + arg);
            }
            result.put(arg.substring(0, idx), arg.substring(idx + 1));
        }
        return result;
    }

    public void build() throws Exception {
        Source source;

        source = Source.createOpt(console, world.getWorking().join(explicitApp));
        if (source == null) {
            throw new IOException("no war found");
        }
        try (Daemon daemon = Daemon.create()) {
            source.build(daemon, "todo:registryprefix", "todo:stage",
                    comment, keep, noCache, explicitArguments);
        }
    }
}
