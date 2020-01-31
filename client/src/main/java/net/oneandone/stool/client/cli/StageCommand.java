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

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.client.Globals;

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
        EnumerationFailed failures;
        String failureMessage;

        failures = runAll();
        failureMessage = failures.getMessage();
        if (failureMessage != null) {
            switch (fail) {
                case AFTER:
                    throw failures;
                case NEVER:
                    console.info.println("WARNING: " + failureMessage);
                    break;
                default:
                    throw new IllegalStateException("unknown fail mode: " + fail.toString());
            }
        }
    }

    public abstract EnumerationFailed runAll() throws Exception;

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
