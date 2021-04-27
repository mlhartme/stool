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

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Executes scripts. Toolkit is a factory for executors.
 *
 * Working is a directory that starts empty when a stage is created, scripts may store data inside, and the
 * resulting directory is preserved as a special value in the helm release. Publishing - i.e. re-running scripts
 * start with the working directory store with the previous release.
 */
public abstract class Executor implements AutoCloseable {
    public final Map<String, String> environment;
    public final FileNode working;

    public Executor(Map<String, String> environment, FileNode working) {
        this.environment = environment;
        this.working = working;
    }

    public abstract String exec(Script script, List<String> args) throws IOException;

    public abstract void close() throws IOException;
}
