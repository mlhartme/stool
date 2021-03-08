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

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.cli.Reference;
import net.oneandone.stool.directions.ClassRef;
import net.oneandone.stool.util.Diff;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Publish extends IteratedStageCommand {
    private final boolean dryrun;
    private final String allow;
    private final ClassRef classRefOpt;
    private final Map<String, String> values;

    public Publish(Globals globals, boolean dryrun, String allow, String stage, List<String> classAndVariables) throws IOException {
        super(globals, stage);
        this.dryrun = dryrun;
        this.allow = allow;
        this.classRefOpt = eatClassRefOpt(classAndVariables);
        this.values = eatValues(classAndVariables);
    }

    private ClassRef eatClassRefOpt(List<String> args) throws IOException {
        if (args.isEmpty() || args.get(0).contains("=")) {
            return null;
        }
        return ClassRef.create(globals.getWorld(), args.remove(0));
    }

    private static Map<String, String> eatValues(List<String> args) {
        Map<String, String> result;
        int idx;
        String arg;
        String key;
        String value;

        result = new LinkedHashMap<>();
        for (int i = 0; i < args.size(); i++) {
            arg = args.get(i);
            idx = arg.indexOf('=');
            if (idx == -1) {
                throw new ArgumentException("expected <key>=<value>, got " + arg);
            }
            key = arg.substring(0, idx);
            value = arg.substring(idx + 1);
            if (result.put(key, value) != null) {
                throw new ArgumentException("duplicate key: " + key);
            }
            args.remove(i);
        }
        return result;
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        Diff result;

        result = reference.client.publish(reference.stage, dryrun, allow, classRefOpt, values);
        if (dryrun) {
            console.info.println("dryrun, changes would be:");
            console.info.println(result.toString());
        } else {
            console.info.println(result.toString());
            console.info.println("done");
        }
    }
}
