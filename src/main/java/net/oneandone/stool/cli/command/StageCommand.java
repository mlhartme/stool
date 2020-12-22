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

import java.util.List;

public abstract class StageCommand extends ClientCommand {
    protected String stageClause;
    protected boolean all;
    protected Fail fail = Fail.NORMAL;

    public StageCommand(Globals globals) {
        super(globals);
    }

    public void setStage(String clause) {
        this.stageClause = clause;
    }

    public void setAll(boolean all) {
        this.all = all;
    }

    public void setFail(Fail fail) {
        this.fail = fail;
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
            switch (fail) {
                case AFTER:
                    throw result;
                case NEVER:
                    console.info.println("WARNING: " + failure);
                    break;
                default:
                    throw new IllegalStateException("unknown fail mode: " + fail.toString());
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
