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

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;

import java.io.IOException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * This is the place for 1&amp;1 specific stuff ...
 */
public class Autoconf {
    public static ServerConfiguration stool(FileNode home, Writer log) throws IOException {
        ServerConfiguration result;

        result = new ServerConfiguration();
        result.secrets = home.getWorld().getHome().join(".fault").getAbsolute();
        result.hostname = hostname();
        oneAndOne(home, result, log);
        return result;
    }

    private static void oneAndOne(FileNode home, ServerConfiguration dest, Writer log) throws IOException {
        String tools;
        FileNode templates;

        tools = System.getenv("CISOTOOLS_HOME");
        if (tools != null) {
            dest.registryNamespace = "contargo.server.lan/mhm";
            dest.ldapUnit = "cisostages";
            dest.certificate = home.getWorld().file(tools).join("stool/templates-5/selfsigned.sh"); // TODO
            dest.admin = "michael.hartmeier@1und1.de";
            dest.mailHost = "mri.server.lan";
            // note: doesn't work on local machines, only for stages ...
            // dest.certificates = "https://itca.server.lan/cgi-bin/cert.cgi?action=create%20certificate&cert-commonName=";

            templates = home.getWorld().file(tools).join("stool/templates-5");
            if (templates.isDirectory()) {
                templates.link(home.join("templates").deleteTree());
            }
        }
    }

    private static String hostname() throws UnknownHostException {
        return InetAddress.getLocalHost().getCanonicalHostName();
    }

}
