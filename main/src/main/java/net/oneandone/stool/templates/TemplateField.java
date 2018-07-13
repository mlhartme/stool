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
package net.oneandone.stool.templates;

import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Field;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class TemplateField extends Field {
    /** @return name- to method name map */
    public static List<TemplateField> scanTemplate(Stage stage, FileNode directory) throws IOException {
        FileNode file;
        List<TemplateField> result;
        TemplateField f;
        String prefix;

        result = new ArrayList<>();
        prefix = directory.getName() + ".";
        file = directory.join("Dockerfile.fm");
        if (file.isFile()) {
            for (String line : file.readLines()) {
                f = TemplateField.parseOpt(stage, prefix, line);
                if (f != null) {
                    result.add(f);
                }
            }
        }
        return result;
    }

    public static TemplateField parseOpt(Stage stage, String prefix, String line) throws IOException {
        List<String> lst;

        line = line.trim();
        if (!line.startsWith("#STATUS")) {
            return null;
        }
        lst = Separator.SPACE.split(line.trim());
        if (lst.size() != 3) {
            throw new IOException("invalid status directive, expected '#STATUS <name> <method>', got '" + line + "'");
        }
        return new TemplateField(prefix + lst.get(1), stage, lst.get(2));
    }

    private final Stage stage;
    private final String method;

    private TemplateField(String name, Stage stage, String method) {
        super(name);
        this.stage = stage;
        this.method = method;
    }

    @Override
    public Object get() throws IOException {
        StatusHelper target;
        Method m;

        target = new StatusHelper(stage, stage.state(), stage.loadPortsOpt());
        try {
            m = target.getClass().getDeclaredMethod(method, new Class[]{});
        } catch (NoSuchMethodException e) {
            throw new IOException("method not found: " + method);
        }
        try {
            return m.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IOException("cannot get method: " + e.getMessage(), e);
        }
    }
}
