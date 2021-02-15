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
package net.oneandone.stool.helmclasses;

import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateSequenceModel;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.Configuration;
import net.oneandone.stool.core.Stage;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Expressions {
    public final World world;
    public final Configuration configuration;
    private final String fqdn;

    /** value is
     *   string when ready
     *   valuetype when to do
     *   null when recursion started
     */
    private Map<String, Object> context;
    private Clazz contextClass;
    private FileNode contextChart;
    private Map<String, String> contextPrevious;

    public Expressions(World world, Configuration configuration, String fqdn) {
        this.world = world;
        this.configuration = configuration;
        this.fqdn = fqdn;
        this.context = null;
        this.contextClass = null;
        this.contextChart = null;
        this.contextPrevious = null;
    }

    public Map<String, String> eval(Map<String, String> previous, Clazz clazz, FileNode chart) {
        Map<String, String> result;

        if (context != null) {
            throw new IllegalStateException();
        }
        context = new LinkedHashMap<>();
        contextClass = clazz;
        contextChart = chart;
        try {
            for (FileNode file : contextChart.find("scripts/*.sh")) {
                if (file.isFile() && !file.toPath().toFile().canExecute()) {
                    file.setPermissions("rwxr-xr-x");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("TODO", e);
        }
        contextPrevious = previous;
        try {
            for (Field field : clazz.fields.values()) {
                context.put(field.name, field);
            }
            for (Field field : clazz.fields.values()) {
                evalValue(field.name);
            }
            result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                result.put(entry.getKey(), (String) entry.getValue());
            }
            return result;
        } finally {
            context = null;
            contextClass = null;
            contextChart = null;
            contextPrevious = null;
        }
    }

    private String evalValue(String name) {
        Object obj;
        String result;

        if (!context.containsKey(name)) {
            throw new ArgumentException("unknown field: " + name);
        }
        obj = context.get(name);
        if (obj == null) {
            throw new ArgumentException("invalid recursion on field " + name);
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        if (obj instanceof Field) {
            context.put(name, null);
            try {
                result = eval(((Field) obj).value);
            } catch (IOException e) {
                throw new ArgumentException("failed to compute value: " + name, e);
            }
            context.put(name, result);
            return result;
        }
        throw new IllegalStateException(obj.getClass().toString());
    }

    public String eval(String str) throws IOException {
        freemarker.template.Configuration fm;
        FileNode srcfile;
        FileNode destfile;
        Template template;
        StringWriter tmp;

        fm = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_26);
        fm.setDefaultEncoding("UTF-8");

        srcfile = world.getTemp().createTempFile();
        srcfile.writeString(str);
        destfile = world.getTemp().createTempFile();
        try {
            fm.setDirectoryForTemplateLoading(srcfile.getParent().toPath().toFile());
            template = fm.getTemplate(srcfile.getName());
            tmp = new StringWriter();
            template.process(templateEnv(), tmp);
            destfile.writeString(tmp.getBuffer().toString());
            return destfile.readString();
        } catch (TemplateException e) {
            throw new IOException("freemarker error: " + e.getMessage(), e);
        } finally {
            destfile.deleteFile();
            srcfile.deleteFile();
        }
    }

    private Map<String, Object> templateEnv() {
        Map<String, Object> result;

        result = new HashMap<>();
        result.put("fqdn", fqdn);
        result.put("defaultExpire", configuration.defaultExpire);
        result.put("defaultContact", Stage.NOTIFY_FIRST_MODIFIER);
        result.put("workdir", (TemplateMethodModelEx) list -> {
            if (list.size() != 1) {
                throw new ArgumentException(list.toString());
            }
            try {
                return configuration.lib.join("workdir", list.get(0).toString()).mkdirsOpt().getAbsolute();
            } catch (IOException e) {
                throw new TemplateModelException(e.getMessage(), e);
            }
        });
        result.put("exec", (TemplateMethodModelEx) list -> {
            try {
                return exec(list);
            } catch (IOException e) {
                throw new TemplateModelException(e.getMessage(), e);
            }
        });
        result.put("commaList", (TemplateMethodModelEx) list -> {
            if (list.size() != 1) {
                throw new ArgumentException(list.toString());
            }
            return Separator.COMMA.split(list.get(0).toString());
        });
        result.put("switch", (TemplateMethodModelEx) list -> {
                    List<String> lst;
                    String var;
                    String dflt;

                    if (list.size() < 2) {
                        throw new ArgumentException(list.toString());
                    }
                    lst = strings(list);
                    var = lst.remove(0);
                    dflt = lst.remove(0);
                    return swtch(var, dflt, lst);
                });
        result.put("env", (TemplateMethodModelEx) list -> {
            String name;
            String value;
            String dflt;

            if (list.size() == 2) {
                dflt = list.get(1).toString();
            } else {
                dflt = null;
                if (list.size() != 1) {
                    throw new ArgumentException(list.toString());
                }
            }
            name = list.get(0).toString();
            value = configuration.environment.get(name);
            if (value == null) {
                if (dflt == null) {
                    throw new TemplateModelException("env variable not found: " + name);
                }
                value = dflt;
            }
            return value;
        });
        result.put("value", (TemplateMethodModelEx) list -> {
            if (list.size() != 1) {
                throw new ArgumentException(list.toString());
            }
            return evalValue(list.get(0).toString());
        });
        result.put("prev", (TemplateMethodModelEx) list -> {
            if (list.size() != 1) {
                throw new ArgumentException(list.toString());
            }
            if (contextPrevious == null) {
                throw new IllegalStateException("missing context");
            }
            return contextPrevious.get(list.get(0).toString());
        });
        result.put("first", (TemplateMethodModelEx) list -> {
            if (contextPrevious == null) {
                throw new IllegalStateException("missing context");
            }
            for (Object obj : list) {
                if (obj != null) {
                    return obj;
                }
            }
            return null;
        });
        return result;
    }

    private String swtch(String var, String dflt, List<String> keyValues) throws TemplateModelException {
        String v;

        v = configuration.environment.get(var);
        if (v == null) {
            throw new TemplateModelException("env variable not found: " + var);
        }
        for (int i = 0; i < keyValues.size(); i += 2) {
            if (v.equals(keyValues.get(i))) {
                return keyValues.get(i + 1);
            }
        }
        return dflt;
    }
    private List<String> strings(List lst) {
        List<String> result;

        result = new ArrayList<>();
        for (Object object : lst) {
            result.add(object.toString());
        }
        return result;
    }

    private String exec(List lst) throws IOException {
        FileNode cmd;
        Launcher script;

        if (lst.isEmpty()) {
            throw new ArgumentException("exec without command");
        }
        if (contextChart == null) {
            throw new ArgumentException("missing chart context");
        }
        cmd = contextChart.join("scripts", lst.get(0).toString());
        if (!cmd.isFile()) {
            throw new ArgumentException("command not found: " + cmd.getAbsolute());
        }
        script = cmd.getParent().launcher();
        script.arg(cmd.getAbsolute());
        add(script, lst.subList(1, lst.size()));
        return script.exec();
    }

    private static void add(Launcher launcher, List lst) {
        for (Object obj : lst) {
            if (obj instanceof List) {
                add(launcher, (List) obj);
            } else if (obj instanceof TemplateSequenceModel) {
                add(launcher, toList((TemplateSequenceModel) obj));
            } else {
                launcher.arg(obj.toString());
            }
        }
    }
    private static List toList(TemplateSequenceModel lst) {
        List result;
        int max;

        try {
            max = lst.size();
        } catch (TemplateModelException e) {
            throw new IllegalStateException(e);
        }
        result = new ArrayList<>(max);
        for (int i = 0; i < max; i++) {
            result.add(result.get(i));
        }
        return result;
    }
}
