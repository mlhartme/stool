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
package net.oneandone.stool.setup;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

public class Dashboard {
    public static void main(String[] args) throws Exception {
        World world;
        FileNode target;
        FileNode war;

        if (args.length != 1) {
            throw new IllegalArgumentException();
        }
        world = World.create();
        target = world.file(args[0]);
        target.mkdir();

        // TODO
        war = world.guessProjectHome(Dashboard.class).getParent().join("dashboard/target").findOne("*.war");

        war.copyFile(target.join("dashboard.war"));
        System.exit(0);
    }
}
