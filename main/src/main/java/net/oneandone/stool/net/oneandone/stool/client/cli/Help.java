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
package net.oneandone.stool.net.oneandone.stool.client.cli;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public class Help {
    private final World world;
    private final String command;

    public Help(Globals globals, String command) {
        this.world = globals.world;
        this.command = command;
    }

    public void run() throws Exception {
        ProcessBuilder builder;
        Process process;
        FileNode tmp;

        builder = new ProcessBuilder();
        builder.directory(null /* use current directory */);
        builder.command("less");
        tmp = format();
        builder.redirectInput(tmp.toPath().toFile());
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        process = builder.start();
        process.waitFor();
    }

    private FileNode format() throws Exception {
        ProcessBuilder builder;
        Process process;
        String name;
        FileNode tmp;

        builder = new ProcessBuilder();
        builder.directory(null /* use current directory */);
        builder.command("groff", "-man", "-Tascii");
        tmp = world.getTemp().createTempFile();
        builder.redirectInput(ProcessBuilder.Redirect.PIPE);
        builder.redirectOutput(tmp.toPath().toFile());
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        process = builder.start();
        name = command == null ? "stool" : "stool-" + command;
        try (InputStream src = world.resource("templates/man/man1/" + name + ".1.gz").newInputStream()) {
            try (GZIPInputStream unzip = new GZIPInputStream(src)) {
                world.getBuffer().copy(unzip, process.getOutputStream());
            }
        }
        process.getOutputStream().close();
        process.waitFor();
        return tmp;
    }
}
