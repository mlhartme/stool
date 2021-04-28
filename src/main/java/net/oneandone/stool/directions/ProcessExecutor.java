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

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** execute scripts in a local process; use this for development and fast turn-arounds */
public class ProcessExecutor extends Executor {
    public ProcessExecutor(Map<String, String> environment, FileNode storageRoot) {
        super(environment, storageRoot);
    }

    public String exec(Script script, List<String> args) throws IOException {
        Launcher launcher;

        launcher = script.file.getParent().launcher();
        launcher.arg(script.file.getAbsolute());
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            launcher.env(entry.getKey(), entry.getValue());
        }
        for (String arg : args) {
            if (Freemarker.STORAGE.equals(arg)) {
                launcher.arg(storageRoot.join(script.name).mkdirsOpt().getAbsolute());
            } else {
                launcher.arg(arg);
            }
        }
        return launcher.exec();
    }

    public void close() {
        // nothing to do
    }
}
