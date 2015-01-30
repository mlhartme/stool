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
package net.oneandone.sales.tools.stool.stage.artifact;

import java.io.IOException;
public class XMLChangeCollector {


    private final WarFile current;
    private final WarFile future;

    public XMLChangeCollector(WarFile current, WarFile future) {

        this.current = current;
        this.future = future;
    }
    //TODO
    public Changes collect() throws IOException {
        StringBuilder message;
        Changes changes;

        message = new StringBuilder();
        message.append("New Version available\n").append(current.version()).append(" -> ").append(future.version());
        changes = new Changes();
        changes.add(new Change(0, null, message.toString(), System.currentTimeMillis()));
        return changes;
    }

}
