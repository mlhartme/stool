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
package net.oneandone.sales.tools.stool.util;

import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class ServerXmlTest {
    private static final World WORLD = new World();

    @Test
    public void getAppUrlstomcat7() throws Exception {
        Node serverXml = WORLD.resource("server.xml");
        Map<String, String> appUrls = ServerXml.load(serverXml).allUrls("/abc");
        Assert.assertEquals(2, appUrls.size());
    }
}
