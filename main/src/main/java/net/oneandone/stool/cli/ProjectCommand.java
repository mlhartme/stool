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
import net.oneandone.stool.stage.Project;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class ProjectCommand extends SessionCommand {
    private final Mode backstageLock;
    private final boolean withAutoRunning;

    private boolean autoRestart;
    private boolean autoStop;
    private String stageClause;
    private boolean all;
    private Fail fail = Fail.NORMAL;

    public ProjectCommand(boolean withAutoRunning, Session session, Mode portsLock, Mode backstageLock) {
        super(session, portsLock);
        this.withAutoRunning = withAutoRunning;
        this.backstageLock = backstageLock;
    }

    /** derived classes override this if the answer is not static */
    public boolean withAutoRunning() {
        return withAutoRunning;
    }

    public void setAutoRestart(boolean autoRestart) {
        this.autoRestart = autoRestart;
    }

    public void setAutoStop(boolean autoStop) {
        this.autoStop = autoStop;
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
        List<Project> lst;
        EnumerationFailed failures;
        String failureMessage;
        boolean withPrefix;
        Worker worker;

        if (autoStop && autoRestart) {
            throw new ArgumentException("ambiguous options: you cannot use both -autostop and -autorestart");
        }
        failures = new EnumerationFailed();
        lst = selected(failures);
        failureMessage = failures.getMessage();
        if (failureMessage != null && fail == Fail.NORMAL) {
            throw failures;
        }
        width = 0;
        for (Project project : lst) {
            width = Math.max(width, project.getStage().getName().length());
        }
        width += 5;
        withPrefix = doBefore(stages(lst), width);
        worker = new Worker(width, failures, withPrefix);
        for (Project project : lst) {
            worker.main(project);
        }
        for (Project project : lst) {
            worker.finish(project);
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

    private static List<Stage> stages(List<Project> projects) {
        List<Stage> result;

        result = new ArrayList<>();
        for (Project project : projects) {
            result.add(project.getStage());
        }
        return result;
    }

    private List<Project> selected(EnumerationFailed problems) throws IOException {
        int count;

        count = (stageClause != null ? 1 : 0) + (all ? 1 : 0);
        switch (count) {
            case 0:
                return defaultSelected(problems);
            case 1:
                if (all) {
                    return all(problems);
                } else if (stageClause != null) {
                    return session.list(problems, or(stageClause));
                } else {
                    throw new IllegalStateException();
                }
            default:
                throw new ArgumentException("too many select options");
        }
    }


    protected List<Project> all(EnumerationFailed problems) throws IOException {
        return session.list(problems, new Predicate() {
            @Override
            public boolean matches(Project project) {
                return true;
            }
        });
    }

    protected Project selected() throws IOException {
        String id;

        id = session.getSelectedStageId();
        if (id == null) {
            throw new IOException("no stage selected");
        }
        return session.load(id);
    }

    /** override this to change the default */
    protected List<Project> defaultSelected(EnumerationFailed notUsed) throws IOException {
        return Collections.singletonList(selected());
    }

    /* Note that the stage is not locked when this method is called. @return true to use prefix stream. */
    public boolean doBefore(List<Stage> stages, int indent) throws IOException {
        return stages.size() != 1;
    }

    private boolean autoStart(Project project) throws Exception {
        Project.State state;
        boolean postStart;

        state = project.stage.state();
        if (state == Project.State.UP && (withAutoRunning()) && (autoRestart || autoStop)) {
            postStart = autoRestart;
            new Stop(session).doRun(project);
        } else {
            postStart = false;
        }
        return postStart;
    }


    public final void doRun(Project project) throws Exception {
        doMain(project);
        doFinish(project);
    }

    /** main method to perform this command */
    public abstract void doMain(Project project) throws Exception;

    /** override this if your doMain method needs some finishing */
    public void doFinish(Project project) throws Exception {
    }

    //--

    /* Note that the stage is not locked when this method is called. */
    public void doAfter() throws IOException {
    }

    public enum Fail {
        NORMAL, AFTER, NEVER
    }


    //--

    private Predicate or(String string) {
        List<String> args;

        args = Separator.COMMA.split(string);
        return new Predicate() {
            @Override
            public boolean matches(Project project) throws IOException {
            Predicate op;

            for (String arg : args) {
                op = and(arg);
                if (op.matches(project)) {
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
            public boolean matches(Project project) throws IOException {
            Predicate op;

            for (String arg : args) {
                op = compare(project, arg);
                if (!op.matches(project)) {
                    return false;
                }
            }
            return true;
            }
        };
    }


    private Predicate compare(Project project, final String string) throws IOException {
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
                public boolean matches(Project project) {
                    return project.getStage().getName().equals(string);
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
        field = project.fieldOpt(name);
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
            public boolean matches(Project project) throws IOException {
                boolean result;
                Object obj;
                String str;
                Property p;

                if (constField != null) {
                    obj = field.get();
                } else {
                    p = project.getStage().propertyOpt(constProperty);
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
        private final int width;
        private final EnumerationFailed failures;
        private final boolean withPrefix;
        private final Map<Project, Start> postStarts;

        public Worker(int width, EnumerationFailed failures, boolean withPrefix) {
            this.width = width;
            this.failures = failures;
            this.withPrefix = withPrefix;
            this.postStarts = new LinkedHashMap<>();
        }

        public void main(Project project) throws Exception {
            run(project, true);
        }
        public void finish(Project project) throws Exception {
            run(project, false);
        }

        private void run(Project project, boolean main) throws Exception {
            try (Lock lock1 = createLock(project.getStage().backstageLock(), backstageLock)) {
                if (withPrefix) {
                    ((PrefixWriter) console.info).setPrefix(Strings.padLeft("{" + project.getStage().getName() + "} ", width));
                }
                session.logging.setStage(project.getStage().getId(), project.getStage().getName());

                if (main) {
                    runMain(project);
                } else {
                    runFinish(project);
                }
            } catch (Error | RuntimeException e) {
                console.error.println(project.getStage().getName() + ": " + e.getMessage());
                throw e;
            } catch (Exception e /* esp. ArgumentException */) {
                if (fail == Fail.NORMAL) {
                    throw e;
                }
                failures.add(project, e);
            } finally {
                session.logging.setStage("", "");
                if (console.info instanceof PrefixWriter) {
                    ((PrefixWriter) console.info).setPrefix("");
                }
            }
        }

        private void runMain(Project project) throws Exception {
            console.verbose.println("*** stage main");
            if (autoStart(project)) {
                postStarts.put(project, new Start(session, false, false));
            }
            doMain(project);
        }

        private void runFinish(Project project) throws Exception {
            Start postStart;

            console.verbose.println("*** stage finish");
            doFinish(project);
            postStart = postStarts.get(project);
            if (postStart != null) {
                if (!project.getDirectory().exists()) {
                    // stage removed -- no need to start again
                    return;
                }
                postStart.doRun(project);
            }
        }
    }
}
