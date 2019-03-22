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
import net.oneandone.stool.locking.Lock;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Reference;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Field;
import net.oneandone.stool.util.Predicate;
import net.oneandone.stool.util.Processes;
import net.oneandone.stool.util.Property;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.io.PrefixWriter;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class StageCommand extends ClientCommand {
    private final Mode lock;

    private String stageClause;
    private boolean all;
    private Fail fail = Fail.NORMAL;

    public StageCommand(Session session, Mode portsLock, Mode lock) {
        super(session, portsLock);
        this.lock = lock;
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
            width = Math.max(width, sessionTodo.load(reference).getName().length());
        }
        width += 5;
        withPrefix = doBefore(lst, width);
        worker = new Worker(sessionTodo, width, failures, withPrefix);
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
                    return all(problems);
                } else if (stageClause != null) {
                    return server.list(problems, or(stageClause));
                } else {
                    throw new IllegalStateException();
                }
            default:
                throw new ArgumentException("too many select options");
        }
    }


    protected List<Reference> all(EnumerationFailed problems) throws IOException {
        return server.list(problems, new Predicate() {
            @Override
            public boolean matches(Stage stage) {
                return true;
            }
        });
    }

    private Reference selected() throws IOException {
        String id;

        id = sessionTodo.getSelectedStageId();
        if (id == null) {
            throw new IOException("no stage selected");
        }
        return sessionTodo.loadById(id).reference;
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

    private Predicate or(String string) {
        List<String> args;

        args = Separator.COMMA.split(string);
        return new Predicate() {
            @Override
            public boolean matches(Stage stage) throws IOException {
                Predicate op;

                for (String arg : args) {
                    op = and(arg);
                    if (op.matches(stage)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    private static final Separator AND = Separator.on('+');

    private Predicate and(String string) {
        List<String> args;

        args = AND.split(string);
        return new Predicate() {
            @Override
            public boolean matches(Stage stage) throws IOException {
                Predicate op;

                for (String arg : args) {
                    op = compare(stage, arg);
                    if (!op.matches(stage)) {
                        return false;
                    }
                }
                return true;
            }
        };
    }


    private Predicate compare(Stage stage, final String string) throws IOException {
        int idx;
        String name;
        final boolean eq;
        Field field;
        final Field constField;
        String value;
        String property;
        final String constProperty;
        final boolean suffix;
        final boolean prefix;
        final String constValue;

        idx = string.indexOf('=');
        if (idx == -1) {
            return new Predicate() {
                @Override
                public boolean matches(Stage stage) {
                    return stage.getName().equals(string);
                }
            };
        }
        if (idx > 0 && string.charAt(idx - 1) == '!') {
            eq = false;
            name = string.substring(0, idx - 1);
        } else {
            eq = true;
            name = string.substring(0, idx);
        }
        field = stage.fieldOpt(name);
        if (field != null) {
            property = null;
        } else {
            property = name;
        }
        constField = field;
        constProperty = property;
        value = string.substring(idx + 1);
        if (value.startsWith("*")) {
            prefix = false;
            value = value.substring(1);
        } else {
            prefix = true;
        }
        if (value.endsWith("*")) {
            suffix = false;
            value = value.substring(0, value.length() - 1);
        } else {
            suffix = true;
        }
        constValue = value;
        return new Predicate() {
            @Override
            public boolean matches(Stage stage) throws IOException {
                boolean result;
                Object obj;
                String str;
                Property p;

                if (constField != null) {
                    obj = field.get();
                } else {
                    p = stage.propertyOpt(constProperty);
                    if (p == null) {
                        throw new PredicateException("property or status field not found: " + constProperty);
                    }
                    obj = p.get();
                }
                if (obj == null) {
                    str = "";
                } else {
                    str = obj.toString();
                }
                if (prefix && suffix) {
                    result = constValue.equals(str);
                } else if (prefix) {
                    result = str.startsWith(constValue);
                } else if (suffix) {
                    result = str.endsWith(constValue);
                } else {
                    result = str.contains(constValue);
                }
                return result == eq;
            }
        };
    }

    /** CAUTION: do not place this in a session, because it doesn't work long-lived sessions (dashboard!) */
    private Processes lazyProcesses = null;

    public Processes processes() throws Failure {
        if (lazyProcesses == null) {
            lazyProcesses = Processes.load(world);
        }
        return lazyProcesses;
    }

    //--

    /** executes a stage command with proper locking */
    public class Worker {
        private final Session sessionTodo;
        private final int width;
        private final EnumerationFailed failures;
        private final boolean withPrefix;
        private final Map<Stage, Start> postStarts;

        public Worker(Session sessionTodo, int width, EnumerationFailed failures, boolean withPrefix) {
            this.sessionTodo = sessionTodo;
            this.width = width;
            this.failures = failures;
            this.withPrefix = withPrefix;
            this.postStarts = new LinkedHashMap<>();
        }

        public void main(Reference reference) throws Exception {
            run(reference, true);
        }
        public void finish(Reference reference) throws Exception {
            run(reference, false);
        }

        private void run(Reference reference, boolean main) throws Exception {
            Stage stage;

            stage = sessionTodo.load(reference);
            if (withPrefix) {
                ((PrefixWriter) console.info).setPrefix(Strings.padLeft("{" + stage.getName() + "} ", width));
            }
            sessionTodo.logging.openStage(stage.reference.getId(), stage.getName());
            try (Lock lock1 = createLock(stage.lock(), lock)) {
                if (main) {
                    runMain(stage);
                } else {
                    runFinish(stage);
                }
            } catch (Error | RuntimeException e) {
                console.error.println(stage.getName() + ": " + e.getMessage());
                throw e;
            } catch (Exception e /* esp. ArgumentException */) {
                if (fail == Fail.NORMAL) {
                    throw e;
                }
                failures.add(stage, e);
            } finally {
                sessionTodo.logging.closeStage();
                if (console.info instanceof PrefixWriter) {
                    ((PrefixWriter) console.info).setPrefix("");
                }
            }
        }

        private void runMain(Stage stage) throws Exception {
            console.verbose.println("*** stage main");
            doMain(stage.reference);
        }

        private void runFinish(Stage stage) throws Exception {
            Start postStart;

            console.verbose.println("*** stage finish");
            doFinish(stage.reference);
            postStart = postStarts.get(stage);
            if (postStart != null) {
                postStart.doRun(stage.reference);
            }
        }
    }
}
