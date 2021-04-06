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
package net.oneandone.stool.util;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TarTest {
    private static final World WORLD = World.createMinimal();

    @Test
    public void empty() throws IOException {
        FileNode root;
        FileNode got;
        String str;

        root = WORLD.getTemp().createTempDirectory();
        str = Tar.fromDir(root);
        got = Tar.toDir(WORLD, str);
        assertTrue(got.list().isEmpty());
    }

    @Test
    public void one() throws IOException {
        FileNode root;
        FileNode got;
        String str;

        root = WORLD.getTemp().createTempDirectory();
        root.join("dir").mkdir().join("file").writeString("hello");
        str = Tar.fromDir(root);
        got = Tar.toDir(WORLD, str);
        assertEquals("hello", got.join("dir/file").readString());
    }
}
