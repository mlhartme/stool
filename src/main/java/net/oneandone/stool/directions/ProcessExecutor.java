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
package net.oneandone.stool.directions;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ProcessExecutor extends Executor {
    public static ProcessExecutor forTesting(World world) throws IOException {
        return new ProcessExecutor(world.getTemp().createTempDirectory());
    }

    public final FileNode working;

    public ProcessExecutor(FileNode working) {
        this.working = working;
    }

    public String exec(Script script, List<String> args, Map<String, String> environment) throws IOException {
        Launcher launcher;

        launcher = script.file.getParent().launcher();
        launcher.arg(script.file.getAbsolute());
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            launcher.env(entry.getKey(), entry.getValue());
        }
        launcher.args(args);
        return launcher.exec();
    }
}
