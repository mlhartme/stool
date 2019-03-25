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
package net.oneandone.stool.cli;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.stage.Reference;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Field;
import net.oneandone.stool.util.Predicate;
import net.oneandone.stool.util.Project;
import net.oneandone.stool.util.Property;
import net.oneandone.stool.util.Server;
import net.oneandone.sushi.io.PrefixWriter;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class StageCommand extends ClientCommand {
    private String stageClause;
    private boolean all;
    private Fail fail = Fail.NORMAL;

    public StageCommand(Server server) {
        super(server);
    }

    public void setStage(String stageClause) {
        this.stageClause = stageClause;
    }

    public void setAll(boolean all) {
        this.all = all;
    }

    public void setFail(Fail fail) {
        this.fail = fail;
    }

    //--

    @Override
    public void doRun() throws Exception {
        int width;
        List<Reference> lst;
        EnumerationFailed failures;
        String failureMessage;
        boolean withPrefix;
        Worker worker;

        failures = new EnumerationFailed();
        lst = selected(failures);
        failureMessage = failures.getMessage();
        if (failureMessage != null && fail == Fail.NORMAL) {
            throw failures;
        }
        width = 0;
        for (Reference reference : lst) {
            width = Math.max(width, getName(reference).length());
        }
        width += 5;
        withPrefix = doBefore(lst, width);
        worker = new Worker(server, width, failures, withPrefix);
        for (Reference reference : lst) {
            worker.main(reference);
        }
        if (this instanceof Remove) {
            // TODO - skip
        } else for (Reference reference : lst) {
            worker.finish(reference);
        }
        doAfter();
        failureMessage = failures.getMessage();
        if (failureMessage != null) {
            switch (fail) {
                case AFTER:
                    throw failures;
                case NEVER:
                    console.info.println("WARNING: " + failureMessage);
                    break;
                default:
                    throw new IllegalStateException(fail.toString());
            }
        }
    }

    private List<Reference> selected(EnumerationFailed problems) throws IOException {
        int count;

        count = (stageClause != null ? 1 : 0) + (all ? 1 : 0);
        switch (count) {
            case 0:
                return defaultSelected(problems);
            case 1:
                if (all) {
                    return server.search(problems, null);
                } else if (stageClause != null) {
                    return server.search(problems, stageClause);
                } else {
                    throw new IllegalStateException();
                }
            default:
                throw new ArgumentException("too many select options");
        }
    }

    private Reference selected() throws IOException {
        Project project;

        project = Project.lookup(world.getWorking());
        return project == null ? null : project.getAttachedOpt();
    }

    /** override this to change the default */
    protected List<Reference> defaultSelected(EnumerationFailed notUsed) throws IOException {
        return Collections.singletonList(selected());
    }

    /* Note that the stage is not locked when this method is called. @return true to use prefix stream. */
    public boolean doBefore(List<Reference> references, int indent) throws IOException {
        return references.size() != 1;
    }

    //--

    /** main method to perform this command */
    public abstract void doMain(Reference stage) throws Exception;

    public void doRun(Reference reference) throws Exception {
        doMain(reference);
        doFinish(reference);
    }

    /** override this if your doMain method needs some finishing */
    public void doFinish(Reference reference) throws Exception {
    }

    /* Note that the stage is not locked when this method is called. */
    public void doAfter() throws IOException {
    }

    public enum Fail {
        NORMAL, AFTER, NEVER
    }


    //--

    protected static Map<String, Integer> selection(List<String> selection) {
        int idx;
        Map<String, Integer> result;

        result = new LinkedHashMap<>();
        for (String appIndex : selection) {
            idx = appIndex.indexOf(':');
            if (idx == -1) {
                result.put(appIndex, 0);
            } else {
                result.put(appIndex.substring(0, idx), Integer.parseInt(appIndex.substring(idx + 1)));
            }
        }
        return result;
    }


    //--

    /** executes a stage command with proper locking */
    public class Worker {
        private final Server server;
        private final int width;
        private final EnumerationFailed failures;
        private final boolean withPrefix;

        public Worker(Server server, int width, EnumerationFailed failures, boolean withPrefix) {
            this.server = server;
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

        private void run(Reference reference, boolean main) throws Exception {
            String name;

            name = getName(reference);
            if (withPrefix) {
                ((PrefixWriter) console.info).setPrefix(Strings.padLeft("{" + name + "} ", width));
            }
            server.openStage(reference, name);
            try {
                if (main) {
                    runMain(reference);
                } else {
                    runFinish(reference);
                }
            } catch (Error | RuntimeException e) {
                console.error.println(name + ": " + e.getMessage());
                throw e;
            } catch (Exception e /* esp. ArgumentException */) {
                if (fail == Fail.NORMAL) {
                    throw e;
                }
                failures.add(name, reference, e);
            } finally {
                server.closeStage();
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
