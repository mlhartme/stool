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
    private final String explicitApp;
    private final boolean noCache;
    private final int keep;
    private final String comment;
    private final Map<String, String> explicitArguments;

    public Build(Globals globals, String explicitApp, boolean noCache, int keep, String comment, List<String> args) {
        super(globals);
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

    @Override
    public void doMain(Reference reference) throws Exception {
        Workspace workspace;
        App app;
        Source source;

        if (explicitApp != null) {
            source = App.parse(explicitApp).source(world.getWorking());
        } else {
            workspace = lookupWorkspace();
            if (workspace == null) {
                throw new ArgumentException("cannot build " + reference + " without a workspace");
            }
            app = workspace.lookup(reference);
            if (app == null) {
                throw new ArgumentException("don't know how to build " + reference + ", it's not attached to this workspace");
            }
            source = app.source(workspace.directory);
        }
        try (Daemon daemon = Daemon.create()) {
            source.build(globals, daemon, reference, comment, keep, noCache, explicitArguments);
        }
    }
}
