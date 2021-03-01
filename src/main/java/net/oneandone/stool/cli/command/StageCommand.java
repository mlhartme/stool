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
package net.oneandone.stool.cli.command;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.cli.Reference;
import net.oneandone.stool.cli.Workspace;

import java.io.IOException;
import java.util.List;

public abstract class StageCommand extends ClientCommand {
    /** empty for all */
    protected String stageClause;
    protected Workspace workspaceOpt;

    public StageCommand(Globals globals, String clause) {
        super(globals);

        workspaceOpt = null;

        if (clause.startsWith("%")) {
            if ("%all".equals(clause)) {
                stageClause = "";
            } else {
                stageClause = clause.substring(1);
            }
        } else if (clause.startsWith("@")) {
            try {
                workspaceOpt = globals.workspaceLoad(clause);
            } catch (IOException e) {
                throw new ArgumentException("failed to loaded workspace: " + e.getMessage(), e);
            }
            stageClause = "";
            for (Reference r : workspaceOpt.references()) { // TODO: different contexts
                if (stageClause.isEmpty()) {
                    stageClause = stageClause + ",";
                }
                stageClause = stageClause + r.stage;
            }
        } else {
            stageClause = clause;
        }
    }

    //--

    @Override
    public void run() throws Exception {
        CompoundResult result;
        String failure;
        int size;

        result = runAll();
        size = result.size();
        if (size == 0) {
            console.info.println("no stage(s)");
        } else {
            console.verbose.println("processed stage: " + size);
        }
        failure = result.getMessage();
        if (failure != null) {
            switch (globals.getFail()) {
                case AFTER:
                    throw result;
                case NEVER:
                    console.info.println("WARNING: " + failure);
                    break;
                default:
                    throw new IllegalStateException("unknown fail mode: " + globals.getFail().toString());
            }
        }
    }

    public abstract CompoundResult runAll() throws Exception;

    public enum Fail {
        NORMAL, AFTER, NEVER
    }

    //--

    protected static String imageOpt(List<String> args) {
        switch (args.size()) {
            case 0:
                return null;
            case 1:
                return args.get(0);
            default:
                throw new ArgumentException("to many image arguments: " + args);
        }
    }
}
