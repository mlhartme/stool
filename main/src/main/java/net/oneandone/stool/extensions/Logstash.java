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
package net.oneandone.stool.extensions;

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Files;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Logstash implements Extension {
    private final String output;

    public Logstash() {
        this("output { stdout {} }");
    }

    public Logstash(String output) {
        this.output = output;
    }

    @Override
    public Map<String, FileNode> vhosts(Stage stage) {
        return new HashMap<>();
    }

    private FileNode conf(Stage stage) {
        return stage.getBackstage().join("logstash.conf");
    }

    private FileNode link(Stage stage) {
        return stage.getDirectory().getWorld().file("/etc/logstash/conf.d/" + stage.getName() + ".conf");
    }

    @Override
    public void beforeStart(Stage stage) throws IOException {
        FileNode file;

        file = conf(stage);
        file.writeString(
                "input {\n" +
                "  file {\n" +
                "    type => 'generic'\n" +
                "    tags => ['" + stage.getName() + "']\n" +
                "    start_position => beginning\n" +
                "    ignore_older => 0\n" +
                "    path => ['" + stage.getBackstage().join(Pustefix.APPLOGS).getAbsolute() + "/*/*.log']\n" +
                "  }\n" +
                "}\n" +
                "\n" +
                "filter {}\n" + output);
        Files.stoolFile(file);
        file.link(link(stage));
    }

    @Override
    public void beforeStop(Stage stage) throws IOException {
        link(stage).deleteFile();
        conf(stage).deleteFile();
    }

    @Override
    public void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result) {
    }
}
