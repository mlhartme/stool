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
package net.oneandone.sales.tools.stool;

import net.oneandone.sushi.cli.Command;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.Writer;

public abstract class BaseCommand implements Command {

    protected final Console console;
    protected final World world;

    public static World config(World world) {
        return world;
    }

    public BaseCommand(Console console) {
        this.console = console;
        this.world = config(console.world);
    }

    @Override
    public abstract void invoke() throws Exception;

    //--

    protected void run(Launcher l, Node output) throws IOException {
        message(l, output instanceof FileNode ? " > " + output : "");
        runQuiet(l, output);
    }

    protected void runQuiet(Launcher l, Node output) throws IOException {
        try (Writer out = output.createWriter()) {
            l.exec(out);
        }
    }

    protected void header(String h) {
        console.info.println("[" + h + "]");
    }

    protected void message(Launcher l, String suffix) {
        message(Separator.SPACE.join(l.getBuilder().command()) + suffix);
    }

    protected void message(String msg) {
        console.info.println(Strings.indent(msg, "  "));
    }

    protected void newline() {
        console.info.println();
    }


}
