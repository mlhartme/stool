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
 * Running scripts can use a storage directory the keeps files between invocations of the script.
 * This directory is managed per script. The first invocation starts with an empty persistent directory.
 * Storage is implemented by special values in the respective helm release.
 */
public abstract class Executor implements AutoCloseable {
    public final Map<String, String> environment;
    public final FileNode storageRoot;

    public Executor(Map<String, String> environment, FileNode storageRoot) {
        this.environment = environment;
        this.storageRoot = storageRoot;
    }

    public abstract String exec(Script script, List<String> args) throws IOException;

    public abstract void close() throws IOException;
}
