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
package com.oneandone.sales.tools.stool.util;

import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.junit.Test;
public class KeyStoreIT {
    @Test
    public void testSignWildcard() throws Exception {
        CertificateAuthority ca;
        FileNode workDir;
        World world;
        Console console;
        SSLKeyStore keyStore;

        world = new World();
        console = Console.create(world);
        workDir = world.getTemp().createTempDirectory();

        ca = new CertificateAuthority(workDir, "*.jenkins.websales.united.domain", console);
        keyStore = new SSLKeyStore(workDir, console);
        keyStore.store(ca.certificate());
    }
}

