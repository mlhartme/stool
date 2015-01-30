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

import net.oneandone.sushi.fs.World;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProcessesTest {
    private static final World WORLD = new World();

    @Test
    public void tomcat() {
        String psline = "mhm             56189   0.0  0.0  2465620    988   ??  S     1:06PM   0:00.25 "
                + "/Users/mhm/Projects/oneandone/sales/tools/wrapper-macosx-universal-64-3.5.26/bin/wrapper "
                + "/Users/mhm/.stool/wrappers/overview/shared/conf/wrapper.conf wrapper.syslog.ident=tomcat "
                + "wrapper.pidfile=/Users/mhm/.stool/wrappers/overview/shared/run/tomcat.pid wrapper.name=tomcat "
                + "wrapper.displayname=Stage overview Tomcat wrapper.daemonize=TRUE "
                + "wrapper.statusfile=/Users/mhm/.stool/wrappers/overview/shared/run/tomcat.status "
                + "wrapper.java.statusfile=/Users/mhm/.stool/wrappers/overview/shared/run/tomcat.java.status "
                + "wrapper.script.version=3.5.22\n"
                + "mhm             4711    0.0  0.0  2465620    988   ??  S     1:06PM   0:00.25 "
                + "/Users/mhm/Projects/oneandone/sales/tools/wrapper-macosx-universal-64-3.5.26/bin/wrapper "
                + "/Users/mhm/.stool/wrappers/two/shared/conf/wrapper.conf wrapper.syslog.ident=tomcat "
                + "wrapper.pidfile=/Users/mhm/.stool/wrappers/two/shared/run/tomcat.pid wrapper.name=tomcat "
                + "wrapper.displayname=Stage two Tomcat wrapper.daemonize=TRUE "
                + "wrapper.statusfile=/Users/mhm/.stool/wrappers/two/shared/run/tomcat.status "
                + "wrapper.java.statusfile=/Users/mhm/.stool/wrappers/two/shared/run/tomcat.java.status "
                + "wrapper.script.version=3.5.22\n";
        tomcat(null, "5", "");
        tomcat("56189", "/Users/mhm/.stool/wrappers/overview", psline);
        tomcat("4711", "/Users/mhm/.stool/wrappers/two", psline);
        tomcat(null, "eue", psline);
    }

    private void tomcat(String expected, String stageWrapper, String psline) {
        assertEquals(expected, new Processes("foo\n" + psline + "bar\n").tomcatPid(WORLD.file(stageWrapper)));
    }
}
