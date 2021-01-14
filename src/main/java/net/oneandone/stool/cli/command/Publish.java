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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Publish extends IteratedStageCommand {
    private final String clazz;
    private final Map<String, String> values;

    public Publish(Globals globals, String stage, String clazz, List<String> args) {
        super(globals, stage);
        this.clazz = clazz;
        this.values = new LinkedHashMap<>();
        eatValues(args);
    }

    private void eatValues(List<String> args) {
        int idx;
        String arg;
        String key;
        String value;

        for (int i = args.size() - 1; i >= 0; i--) {
            arg = args.get(i);
            idx = arg.indexOf('=');
            if (idx == -1) {
                break;
            }
            key = arg.substring(0, idx);
            value = arg.substring(idx + 1);
            if (values.put(key, value) != null) {
                throw new ArgumentException("duplicate key: " + key);
            }
            args.remove(i);
        }
        if (!args.isEmpty()) {
            throw new ArgumentException("unknown arguments: " + args);
        }
    }

    @Override
    public void doMain(Reference reference) throws Exception {
        reference.client.publish(reference.stage, clazz, values);
    }
}
