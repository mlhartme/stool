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
package net.oneandone.stool;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.oneandone.stool.cli.Caller;
import net.oneandone.stool.cli.ProxyClient;
import net.oneandone.stool.cli.Workspace;
import net.oneandone.stool.cli.Reference;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class WorkspaceTest {
    @Test
    public void yaml() throws IOException {
        World world;
        FileNode file;
        Workspace workspace;

        world = World.createMinimal();
        file = world.getTemp().createTempFile();
        workspace = new Workspace(new ObjectMapper(new YAMLFactory()), file);
        workspace.add(new Reference(new ProxyClient(new ObjectMapper(new JsonFactory()),
                "ctx", null /* TODO */, new Caller("a", "b", "c", null)), "stage"));
        workspace.save();
    }
}
