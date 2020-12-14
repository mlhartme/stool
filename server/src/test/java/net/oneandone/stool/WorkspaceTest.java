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

import net.oneandone.stool.client.RemoteClient;
import net.oneandone.stool.client.Workspace;
import net.oneandone.stool.client.Reference;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class WorkspaceTest {
    @Test
    public void yaml() throws IOException {
        World world;
        FileNode dir;
        Workspace workspace;

        world = World.createMinimal();
        dir = world.getTemp().createTempDirectory();
        workspace = Workspace.create(dir);
        workspace.add(new Reference(new RemoteClient("ctx", null /* TODO */), "stage"));
        workspace.save();
    }
}
