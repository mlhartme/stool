/**
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
package com.oneandone.sales.tools.stool.stage.artifact;

import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class Inbox implements ArtifactSource {

    private final FileNode basedir;
    private final String name;
    private final String stage;

    public Inbox(String name, String stage, FileNode basedir) {
        this.basedir = basedir;
        this.name = name;
        this.stage = stage;
    }
    @Override
    public WarFile resolve() throws IOException {
        StringBuilder stringBuilder;
        stringBuilder = new StringBuilder();
        stringBuilder.append(stage).append('_').append(name).append(".war");
        return new WarFile(basedir.join(stringBuilder.toString()));
    }
}
