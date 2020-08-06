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
import net.oneandone.stool.client.App;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Workspace;
import net.oneandone.stool.client.Reference;
import net.oneandone.sushi.io.PrefixWriter;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class IteratedStageCommand extends StageCommand {
    public IteratedStageCommand(Globals globals) {
        super(globals);
    }

    //--

    @Override
    public EnumerationFailed runAll() throws Exception {
        List<Reference> lst;
        int width;
        boolean withPrefix;
        EnumerationFailed failures;
        Worker worker;

        lst = selectedList();
        width = 0;
        for (Reference reference : lst) {
            width = Math.max(width, reference.toString().length());
        }
        width += 5;
        withPrefix = lst.size() != 1;
        failures = new EnumerationFailed();
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

    private List<Reference> selectedList() throws IOException {
        int count;

        count = (stageClause != null ? 1 : 0) + (all ? 1 : 0);
        switch (count) {
            case 0:
                return defaultSelected();
            case 1:
                return globals.configuration().list(all ? null : stageClause);
            default:
                throw new ArgumentException("too many select options");
        }
    }

    /** override this to change the default */
    private List<Reference> defaultSelected() throws IOException {
        Workspace workspace;
        List<Reference> result;

        workspace = lookupWorkspace();
        result = new ArrayList<>();
        if (workspace != null) {
            for (App app : workspace.list()) {
                result.add(app.reference);
            }
        }
        return result;
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
        private final EnumerationFailed failures;
        private final boolean withPrefix;

        public Worker(int width, EnumerationFailed failures, boolean withPrefix) {
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
            } catch (ArgumentException e) {
                if (fail == Fail.NORMAL) {
                    throw new ArgumentException(referece + ": " + e.getMessage(), e);
                }
                failures.add(referece, e);
            } catch (Error | RuntimeException e) {
                console.error.println(referece + ": " + e.getMessage());
                throw e;
            } catch (Exception e) {
                if (fail == Fail.NORMAL) {
                    throw e;
                }
                failures.add(referece, e);
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
