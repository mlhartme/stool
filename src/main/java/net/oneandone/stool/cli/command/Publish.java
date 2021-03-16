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
package net.oneandone.stool.cli.command;

import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.cli.Reference;
import net.oneandone.stool.directions.DirectionsRef;
import net.oneandone.stool.util.Diff;
import net.oneandone.stool.util.Misc;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Publish extends IteratedStageCommand {
    private final boolean dryrun;
    private final String allow;
    private final DirectionsRef directionsRefOpt;
    private final Map<String, String> values;

    public Publish(Globals globals, boolean dryrun, String allow, String stage, List<String> directionsAndVariables) throws IOException {
        super(globals, stage);
        this.dryrun = dryrun;
        this.allow = allow;
        this.directionsRefOpt = eatDirectionsRefOpt(directionsAndVariables);
        this.values = Misc.assignments(directionsAndVariables);
    }

    private DirectionsRef eatDirectionsRefOpt(List<String> args) throws IOException {
        if (args.isEmpty() || args.get(0).contains("=")) {
            return null;
        }
        return DirectionsRef.create(globals.getWorld(), args.remove(0));
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        Diff result;

        result = reference.client.publish(reference.stage, dryrun, allow, directionsRefOpt, values);
        if (dryrun) {
            console.info.println("dryrun, changes would be:");
            console.info.println(result.toString());
        } else {
            console.info.println(result.toString());
            console.info.println("done");
        }
    }
}
