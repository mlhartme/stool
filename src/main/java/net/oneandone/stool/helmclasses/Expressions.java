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
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.Configuration;
import net.oneandone.stool.core.Stage;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarHeader;
import org.kamranzafar.jtar.TarOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;

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
        result.put("env", (TemplateMethodModelEx) list -> {
            String name;
            String value;

            if (list.size() != 1) {
                throw new ArgumentException(list.toString());
            }
            name = list.get(0).toString();
            value = configuration.environment.get(name);
            if (value == null) {
                throw new TemplateModelException("env variable not found: " + name);

            }
            return value;
        });
        result.put("fault", (TemplateMethodModelEx) list -> {
            if (list.size() != 1) {
                throw new ArgumentException(list.toString());
            }
            try {
                return fault(Separator.COMMA.split(list.get(0).toString()));
            } catch (IOException e) {
                throw new TemplateModelException(e.getMessage(), e);
            }
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

    private String exec(List lst) throws IOException {
        String[] array;
        FileNode cmd;
        int i;

        array = new String[lst.size()];
        if (array.length == 0) {
            throw new ArgumentException("exec without command");
        }
        if (contextChart == null) {
            throw new ArgumentException("missing chart context");
        }
        i = 0;
        for (Object obj: lst) {
            array[i++] = obj.toString();
        }
        cmd = contextChart.join("scripts", array[0]);
        if (!cmd.isFile()) {
            throw new ArgumentException("command not found: " + cmd.getAbsolute());
        }
        array[0] = cmd.getAbsolute();
        return cmd.getParent().exec(array);
    }

    /** tar directory into byte array */
    private String fault(List<String> faultProjects) throws IOException {
        List<String> missing;
        FileNode workspace;
        FileNode project;
        TarOutputStream tar;
        byte[] buffer;
        long now;
        String result;

        missing = new ArrayList<>();
        if (configuration.auth() && !faultProjects.isEmpty()) {
            if (contextClass == null || contextClass.author == null) {
                throw new ArgumentException("fault is not accessible without class context");  // TODO: error message
            }
            checkFaultPermissions(world, contextClass.author, faultProjects);
        }
        workspace = world.file("/etc/fault/workspace");
        buffer = new byte[64 * 1024];
        try (ByteArrayOutputStream dest = new ByteArrayOutputStream()) {
            tar = new TarOutputStream(new GZIPOutputStream(dest));
            now = System.currentTimeMillis();
            for (String projectName : faultProjects) {
                project = workspace.join(projectName);
                if (project.isDirectory()) {
                    faultTarAdd(now, buffer, workspace, project, tar);
                } else {
                    missing.add(projectName);
                }
            }
            tar.close();
            result = Base64.getEncoder().encodeToString(dest.toByteArray());
        }
        if (!missing.isEmpty()) {
            throw new ArgumentException("missing secret directories: " + missing);
        }
        return result;
    }

    /** tar directory into byte array */
    private void faultTarAdd(long now, byte[] buffer, FileNode workspace, FileNode project, TarOutputStream tar) throws IOException {
        List<FileNode> all;
        Iterator<FileNode> iter;
        FileNode file;
        int count;

        all = project.find("**/*");
        iter = all.iterator();
        while (iter.hasNext()) {
            file = iter.next();
            if (file.isDirectory()) {
                tar.putNextEntry(new TarEntry(TarHeader.createHeader(file.getRelative(workspace), 0, now, true, 0700)));
                iter.remove();
            }
        }
        iter = all.iterator();
        while (iter.hasNext()) {
            file = iter.next();
            tar.putNextEntry(new TarEntry(TarHeader.createHeader(file.getRelative(workspace), file.size(), now, false, 0700)));
            try (InputStream src = file.newInputStream()) {
                while (true) {
                    count = src.read(buffer);
                    if (count == -1) {
                        break;
                    }
                    tar.write(buffer, 0, count);
                }
            }
        }
    }

    //--

    public static void checkFaultPermissions(World world, String user, List<String> projects) throws IOException {
        Properties permissions;
        String lst;

        if (projects.isEmpty()) {
            return;
        }
        permissions = world.file("/etc/fault/workspace.permissions").readProperties();
        for (String project : projects) {
            lst = permissions.getProperty(project);
            if (lst == null) {
                throw new ArgumentException("fault project unknown or not accessible on this host: " + project);
            }
            if (!Separator.COMMA.split(lst).contains(user)) {
                throw new ArgumentException("fault project " + project + ": permission denied for user " + user);
            }
        }
    }
}
