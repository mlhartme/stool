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
import net.oneandone.sushi.io.PrefixWriter;
import net.oneandone.sushi.util.Strings;

import java.util.List;

public abstract class IteratedStageCommand extends StageCommand {
    public IteratedStageCommand(Globals globals, String stage) {
        super(globals, stage);
    }

    //--

    @Override
    public CompoundResult runAll() throws Exception {
        List<Reference> lst;
        int width;
        boolean withPrefix;
        CompoundResult failures;
        Worker worker;

        lst = globals.settings().list(stageClause, globals.caller());
        width = 0;
        for (Reference reference : lst) {
            width = Math.max(width, reference.toString().length());
        }
        width += 5;
        withPrefix = lst.size() != 1;
        failures = new CompoundResult();
        worker = new Worker(width, failures, withPrefix);
        for (Reference reference : lst) {
            worker.main(reference);
        }
        if (this instanceof Delete) {
            // TODO - skip
        } else {
            for (Reference reference : lst) {
                worker.finish(reference);
            }
        }
        return failures;
    }

    //--

    public void doRun(Reference reference) throws Exception {
        doMain(reference);
        doFinish(reference);
    }

    /** main method to perform this command */
    public abstract void doMain(Reference reference) throws Exception;

    /** override this if your doMain method needs some finishing */
    public void doFinish(Reference reference) throws Exception {
    }

    //--

    /** executes a stage command with proper locking */
    public class Worker {
        private final int width;
        private final CompoundResult failures;
        private final boolean withPrefix;

        public Worker(int width, CompoundResult failures, boolean withPrefix) {
            this.width = width;
            this.failures = failures;
            this.withPrefix = withPrefix;
        }

        public void main(Reference reference) throws Exception {
            run(reference, true);
        }

        public void finish(Reference reference) throws Exception {
            run(reference, false);
        }

        private void run(Reference referece, boolean main) throws Exception {
            if (withPrefix) {
                ((PrefixWriter) console.info).setPrefix(Strings.padLeft("{" + referece + "} ", width));
            }
            try {
                if (main) {
                    runMain(referece);
                } else {
                    runFinish(referece);
                }
                failures.success(referece);
            } catch (ArgumentException e) {
                if (globals.getFail() == Fail.NORMAL) {
                    throw new ArgumentException(referece + ": " + e.getMessage(), e);
                }
                failures.failure(referece, e);
            } catch (Error | RuntimeException e) {
                console.error.println(referece + ": " + e.getMessage());
                throw e;
            } catch (Exception e) {
                if (globals.getFail() == Fail.NORMAL) {
                    throw e;
                }
                failures.failure(referece, e);
            } finally {
                if (console.info instanceof PrefixWriter) {
                    ((PrefixWriter) console.info).setPrefix("");
                }
            }
        }

        private void runMain(Reference reference) throws Exception {
            console.verbose.println("*** stage main");
            doMain(reference);
        }

        private void runFinish(Reference reference) throws Exception {
            console.verbose.println("*** stage finish");
            doFinish(reference);
        }
    }
}
