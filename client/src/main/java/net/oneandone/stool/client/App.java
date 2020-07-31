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
package net.oneandone.stool.client;

import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

/** Mapping between stage and how to build it */
public class App {
    /** assiciated stage on the server */
    public final Reference reference;

    public final Source.Type type;

    /** path (possibly with wildcards) that is applied to locate the war file */
    public final String path;

    public App(Reference reference, Source.Type type, String path) {
        this.reference = reference;
        this.type = type;
        this.path = path;
    }

    public Source source(FileNode directory) throws IOException {
        Source result;

        switch (type) {
            case WAR:
                result = WarSource.createOpt(directory.join(path));
                if (result == null) {
                    throw new IOException("no war found in " + path);
                }
                break;
            case DOCKER:
                result = DockerSource.createOpt(directory.join(path));
                if (result == null) {
                    throw new IOException("no Dockerfile found in " + path);
                }
            default:
                throw new IllegalStateException(type.toString());
        }
        return result;
    }
}
