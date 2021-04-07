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
package net.oneandone.stool.directions;

import freemarker.template.TemplateModelException;
import freemarker.template.TemplateSequenceModel;
import net.oneandone.inline.ArgumentException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Script {
    public static List<Script> forTest(World world, String str) throws IOException {
        FileNode dir;

        dir = world.getTemp().createTempDirectory();
        dir.join("script.sh").writeString(str).setPermissions("rwxr-xr-x");
        return scanOpt(dir);

    }
    public static List<Script> scanOpt(FileNode dir) throws IOException {
        List<Script> result;
        String name;

        result = new ArrayList<>();
        if (dir.exists()) {
            for (FileNode file : dir.list()) {
                name = Strings.removeRight(file.checkFile().getName(), ".sh");
                result.add(new Script(name, file));
            }
        }
        return result;
    }

    public final String name;
    public final FileNode file;

    public Script(String name, FileNode file) {
        this.name = name;
        this.file = file;
    }

    public String exec(Runtime runtime, Map<String, String> environment, List argsRaw) throws IOException {
        List<String> args;

        args = new ArrayList<>();
        args(argsRaw, args);
        return runtime.exec(this, args, environment);
    }

    private static void args(List lst, List<String> result) {
        for (Object obj : lst) {
            if (obj instanceof List) {
                args((List) obj, result);
            } else if (obj instanceof TemplateSequenceModel) {
                args(toList((TemplateSequenceModel) obj), result);
            } else {
                result.add(obj.toString());
            }
        }
    }

    private static List<Object> toList(TemplateSequenceModel lst) {
        List<Object> result;
        int max;

        try {
            max = lst.size();
            result = new ArrayList<>(max);
            for (int i = 0; i < max; i++) {
                result.add(lst.get(i));
            }
            return result;
        } catch (TemplateModelException e) {
            throw new ArgumentException(e.getMessage(), e);
        }
    }
}
