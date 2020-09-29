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
package net.oneandone.stool.util;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;

// TODO: does not work in public
public class Secrets {
    public static Secrets load(World world) throws IOException {
        Properties p;

        p = secrets(world).join("secrets.properties").readProperties();
        return new Secrets(URI.create(get(p, "portus")),
                get(p, "ldapUnit"),
                get(p, "ldapUrl"),
                get(p, "ldapPrincipal"),
                get(p, "ldapCredentials"),
                get(p, "ldapSso"));
    }

    private static String get(Properties p, String name) throws IOException {
        String value;

        value = p.getProperty(name);
        if (value == null) {
            throw new IOException("property not found: " + name);
        }
        return value;
    }

    public static FileNode secrets(World world) throws IOException {
        return world.getHome().join(".fault/net.oneandone.stool:stool").checkDirectory();
    }

    public final URI portus;
    public final String ldapUnit;
    public final String ldapUrl;
    public final String ldapPrincipal;
    public final String ldapCredentials;
    public final String ldapSso;

    private Secrets(URI portus, String ldapUnit, String ldapUrl, String ldapPrincipal, String ldapCredentials, String ldapSso) {
        this.portus = portus;
        this.ldapUnit = ldapUnit;
        this.ldapUrl = ldapUrl;
        this.ldapPrincipal = ldapPrincipal;
        this.ldapCredentials = ldapCredentials;
        this.ldapSso = ldapSso;
    }
}
