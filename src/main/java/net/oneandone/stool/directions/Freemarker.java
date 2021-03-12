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

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateScalarModel;
import freemarker.template.TemplateSequenceModel;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.LocalSettings;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Freemarker {
    private final Configuration configuration;
    public final LocalSettings localSettings;
    private final String fqdn;
    private final String stage;
    private final String host;

    /** key is direction name, value is
     *   string with result when ready
     *   Direction object when to do
     *   null when eval of this key started
     */
    private Map<String, Object> context;
    private FileNode contextScripts;
    private Map<String, String> contextPrevious;

    public Freemarker(LocalSettings localSettings, String stage) {
        this.configuration = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_26);
        this.configuration.setDefaultEncoding("UTF-8");
        this.configuration.setLogTemplateExceptions(false);

        this.localSettings = localSettings;
        this.fqdn = stage + "." + localSettings.fqdn;
        this.stage = stage;
        this.host = localSettings.fqdn;
        this.context = null;
        this.contextScripts = null;
        this.contextPrevious = null;
    }

    public Map<String, String> eval(Map<String, String> previous, Directions directions, FileNode scripts) {
        Map<String, String> result;

        if (context != null) {
            throw new IllegalStateException();
        }
        context = new LinkedHashMap<>();
        contextScripts = scripts;
        contextPrevious = previous;
        try {
            for (Direction direction : directions.directions.values()) {
                context.put(direction.name, direction);
            }
            for (Direction directon : directions.directions.values()) {
                evalValue(directon.name);
            }
            result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                result.put(entry.getKey(), (String) entry.getValue());
            }
            return result;
        } finally {
            context = null;
            contextScripts = null;
            contextPrevious = null;
        }
    }

    private String evalValue(String name) {
        Object obj;
        String result;
        Direction direction;

        if (!context.containsKey(name)) {
            throw new ArgumentException("unknown direction: " + name);
        }
        obj = context.get(name);
        if (obj == null) {
            throw new ArgumentException("invalid recursion on direction " + name);
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        if (obj instanceof Direction) {
            context.put(name, null); // start recursion
            direction = (Direction) obj;
            result = direction.valueOpt();
            if (result == null) {
                try {
                    result = eval(direction.expression);
                } catch (IOException e) {
                    throw new ArgumentException(name + ": failed to compute direction: " + e.getMessage(), e);
                }
            }
            context.put(name, result);
            return result;
        }
        throw new IllegalStateException(obj.getClass().toString());
    }

    public String eval(String str) throws IOException {
        StringWriter dest;

        Template template = new Template("direction", str, configuration);
        try {
            dest = new StringWriter();
            template.process(templateEnv(), dest);
            dest.close();
            return dest.toString();
        } catch (TemplateException e) {
            throw new IOException("freemarker error: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> templateEnv() throws IOException {
        Map<String, Object> result;

        result = new HashMap<>();
        result.put("stool", stool());
        result.put("env", new HashMap<>(localSettings.environment));
        result.put("util", util());
        if (contextScripts != null) {
            result.put("script", scripts());
        }
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
        if (context != null) {
            result.put("direction", directions());
        }
        if (contextPrevious != null) {
            result.put("prev", new HashMap<>(contextPrevious));
        }
        return result;
    }

    private Map<String, Object> directions() {
        Map<String, Object> result;

        result = new HashMap<>();
        for (String name : context.keySet()) {
            result.put(name, new TemplateScalarModel() {
                @Override
                public String getAsString() {
                    return evalValue(name);
                }

                public String toString() {
                    return getAsString();
                }
            });
        }
        return result;
    }

    private Map<String, Object> stool() {
        Map<String, Object> result;

        result = new HashMap<>();
        result.put("fqdn", fqdn);
        result.put("stage", stage);
        result.put("host", host);
        result.put("workdir", (TemplateMethodModelEx) list -> {
            if (list.size() != 1) {
                throw new ArgumentException(list.toString());
            }
            try {
                return localSettings.lib.join("workdir", list.get(0).toString()).mkdirsOpt().getAbsolute();
            } catch (IOException e) {
                throw new TemplateModelException(e.getMessage(), e);
            }
        });
        return result;
    }

    private Map<String, Object> util() {
        Map<String, Object> result;

        result = new HashMap<>();
        result.put("commaList", (TemplateMethodModelEx) list -> {
            if (list.size() != 1) {
                throw new ArgumentException(list.toString());
            }
            return Separator.COMMA.split(list.get(0).toString());
        });
        result.put("toBase64", (TemplateMethodModelEx) list -> {
            if (list.size() != 1) {
                throw new ArgumentException(list.toString());
            }
            return Base64.getEncoder().encodeToString(list.get(0).toString().getBytes(StandardCharsets.UTF_8));
        });
        result.put("fromBase64", (TemplateMethodModelEx) list -> {
            if (list.size() != 1) {
                throw new ArgumentException(list.toString());
            }
            return Base64.getDecoder().decode(list.get(0).toString());
        });
        return result;
    }

    private String swtch(String var, String dflt, List<String> keyValues) throws TemplateModelException {
        String v;

        v = localSettings.environment.get(var);
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

    private Map<String, Object> scripts() throws IOException {
        Map<String, Object> result;

        result = new HashMap<>();
        for (FileNode script : contextScripts.list()) {
            final String name = Strings.removeRight(script.checkFile().getName(), ".sh");
            result.put(name, (TemplateMethodModelEx) list -> {
                try {
                    return exec(script, list);
                } catch (IOException e) {
                    throw new TemplateModelException(name + ": script failed: " + e.getMessage(), e);
                }
            });

        }
        return result;
    }

    private String exec(FileNode script, List args) throws IOException {
        Launcher launcher;

        launcher = script.getParent().launcher();
        launcher.arg(script.getAbsolute());
        add(launcher, args);
        return launcher.exec();
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
