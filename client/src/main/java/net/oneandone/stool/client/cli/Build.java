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

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.client.App;
import net.oneandone.stool.client.Reference;
import net.oneandone.stool.client.Source;
import net.oneandone.stool.docker.Daemon;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Workspace;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Build extends IteratedStageCommand {
    private final boolean noCache;
    private final int keep;
    private final boolean restart;
    private final String comment;
    private final Map<String, String> explicitArguments;

    public Build(Globals globals, boolean noCache, int keep, boolean restart, String comment, List<String> args) {
        super(globals);
        this.noCache = noCache;
        this.keep = keep;
        this.restart = restart;
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

    @Override
    public void doMain(Reference reference) throws Exception {
        Workspace project;
        App app;
        Source source;

        project = lookupProject();
        if (project == null) {
            throw new ArgumentException("cannot build " + reference + " without a project");
        }
        app = project.lookup(reference);
        source = app.source(project.directory);
        try (Daemon daemon = Daemon.create()) {
            source.build(globals, daemon, app.reference, comment, keep, noCache, project.getOriginOrUnknown(), explicitArguments);
        }
        if (restart) {
            new Restart(globals, null).doRun(app.reference);
        }
    }
}
