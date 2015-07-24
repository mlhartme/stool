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
import net.oneandone.sushi.xml.XmlException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Pustefix implements Extension {
    private final String mode;

    public Pustefix() {
        this("test");
    }

    public Pustefix(String mode) {
        this.mode = mode;
    }

    @Override
    public Map<String, FileNode> vhosts(Stage stage) {
        return new HashMap<>();
    }

    private static final String APPLOGS = "tomcat/logs/applogs";

    @Override
    public void beforeStart(Stage stage) throws IOException {
        Files.createBackstageDirectoryOpt(stage.shared().join(APPLOGS));
    }

    @Override
    public void beforeStop(Stage stage) throws IOException {
    }

    @Override
    public void contextParameter(Stage stage, String host, int httpPort, FileNode webinf, Map<String, String> result) throws XmlException {
        String app;

        app = host.substring(0, host.indexOf('.'));
        result.put("mode", mode);
        result.put("logroot", stage.shared().join(APPLOGS, app).getAbsolute());
    }
}
