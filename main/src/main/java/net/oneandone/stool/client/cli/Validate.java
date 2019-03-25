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
package net.oneandone.stool.client.cli;

import net.oneandone.stool.client.Report;
import net.oneandone.stool.server.stage.Reference;
import net.oneandone.stool.server.util.Server;

public class Validate extends StageCommand {
    private final boolean email;
    private final boolean repair;

    private Report report;

    public Validate(Server server, boolean email, boolean repair) {
        super(server);
        this.email = email;
        this.repair = repair;
    }

    @Override
    public void doRun() throws Exception {
        report = new Report();

        server.validateServer(report);
        super.doRun();
        if (report.isEmpty()) {
            console.info.println("validate ok");
        } else {
            report.console(console);
            if (email) {
                server.email(report);
            }
            console.info.println();
            console.info.println("validate failed");
        }
    }


    @Override
    public void doMain(Reference reference) throws Exception {
        server.validateState(reference, report, repair);
    }
}
