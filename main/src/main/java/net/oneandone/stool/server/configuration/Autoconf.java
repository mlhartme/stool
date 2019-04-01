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
package net.oneandone.stool.server.configuration;

import net.oneandone.stool.server.util.Environment;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;
import net.oneandone.sushi.launcher.Launcher;

import java.io.IOException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This is the place for 1&amp;1 specific stuff ...
 */
public class Autoconf {
    public static StoolConfiguration stool(Environment environment, FileNode home, Writer log) throws IOException {
        StoolConfiguration result;

        result = new StoolConfiguration();
        result.secrets = home.getWorld().getHome().join(".fault").getAbsolute();
        result.hostname = hostname();
        oneAndOne(environment, home, result, log);
        return result;
    }

    private static void oneAndOne(Environment environment, FileNode home, StoolConfiguration dest, Writer log) throws IOException {
        String tools;
        FileNode templates;
        FileNode downloadsCache;
        FileNode init;

        tools = cisoTools(environment);
        if (tools != null) {
            dest.registryNamespace = "contargo.server.lan/mhm";
            dest.ldapUnit = "cisostages";
            dest.certificate = home.getWorld().file(tools).join("stool/templates-5/selfsigned.sh"); // TODO
            dest.admin = "michael.hartmeier@1und1.de";
            dest.mailHost = "mri.server.lan";
            // note: doesn't work on local machines, only for stages ...
            // dest.certificates = "https://itca.server.lan/cgi-bin/cert.cgi?action=create%20certificate&cert-commonName=";
            dest.defaults.put("svn:https://svn.1and1.org/svn/controlpanel_app/controlpanel/", cp());

            templates = home.getWorld().file(tools).join("stool/templates-5");
            if (templates.isDirectory()) {
                templates.link(home.join("templates").deleteTree());
            }
            init = home.getWorld().file(tools).join("stool/images/init.sh");
            if (init.isFile()) {
                Launcher launcher;

                log.write("initializing templates\n");
                launcher = new Launcher(init.getParent(), init.getAbsolute());
                launcher.exec(log);
            }
        }
        if (OS.CURRENT == OS.MAC) {
            downloadsCache = home.getWorld().getHome().join("Downloads");
            if (downloadsCache.isDirectory()) {
                downloadsCache.link(home.join("downloads").deleteDirectory());
            }
        }
    }

    private static String cisoTools(Environment environment) {
        return environment.getOpt("CISOTOOLS_HOME");
    }

    private static Map<String, String> cp() {
        Map<String, String> result;

        result = new LinkedHashMap<>();
        result.put("memory", "3000");
        result.put("url", "https://%a.%s.%h:%p/(|internal-login)");
        return result;
    }

    private static String hostname() throws UnknownHostException {
        return InetAddress.getLocalHost().getCanonicalHostName();
    }

}
