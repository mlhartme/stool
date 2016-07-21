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
package net.oneandone.stool.cli;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.configuration.Property;
import net.oneandone.stool.locking.Lock;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Field;
import net.oneandone.stool.util.Info;
import net.oneandone.stool.util.Predicate;
import net.oneandone.stool.util.Processes;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.io.PrefixWriter;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class StageCommand extends SessionCommand {
    private final Mode backstageLock;
    private final Mode directoryLock;
    private final boolean withAutoRunning;
    private final boolean withAutoChowning;

    private boolean autoRechown;
    private boolean autoChown;
    private boolean autoRestart;
    private boolean autoStop;
    private String stageClause;
    private boolean all;
    private Fail fail = Fail.NORMAL;

    public StageCommand(boolean withAutoRunning, boolean withAutoChowning, Session session, Mode globalLock, Mode backstageLock, Mode directoryLock) {
        super(session, globalLock);
        this.withAutoRunning = withAutoRunning;
        this.withAutoChowning = withAutoChowning;
        this.backstageLock = backstageLock;
        this.directoryLock = directoryLock;
    }

    /** derived classes override this if the answer is not static */
    public boolean withAutoRunning() {
        return withAutoRunning;
    }
    /** derived classes override this if the answer is not static */
    public boolean withAutoChowning() {
        return withAutoChowning;
    }

    public void setAutoRechown(boolean autoRechown) {
        this.autoRechown = autoRechown;
    }

    public void setAutoChown(boolean autoChown) {
        this.autoChown = autoChown;
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

    public boolean isNoop(Stage stage) throws IOException {
        return false;
    }

    @Override
    public void doRun() throws Exception {
        int width;
        List<Stage> lst;
        EnumerationFailed failures;
        String failureMessage;
        boolean withPrefix;
        Worker worker;

        if (autoChown && autoRechown) {
            throw new ArgumentException("ambiguous options: you cannot use both -autochown and -autorechown");
        }
        if (autoStop && autoRestart) {
            throw new ArgumentException("ambiguous options: you cannot use both -autostop and -autorestart");
        }
        failures = new EnumerationFailed();
        lst = selected(failures);
        filterNops(lst);
        failureMessage = failures.getMessage();
        if (failureMessage != null && fail == Fail.NORMAL) {
            throw failures;
        }
        width = 0;
        for (Stage stage : lst) {
            width = Math.max(width, stage.getName().length());
        }
        width += 5;
        withPrefix = doBefore(lst, width);
        worker = new Worker(width, failures, withPrefix);
        for (Stage stage : lst) {
            worker.main(stage);
        }
        for (Stage stage : lst) {
            worker.finish(stage);
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

    private void filterNops(List<Stage> lst) throws IOException {
        Iterator<Stage> iter;
        Stage stage;

        iter = lst.iterator();
        while (iter.hasNext()) {
            stage = iter.next();
            if (isNoop(stage)) {
                console.verbose.println("nothing to do: " + stage.getName());
                iter.remove();
            }
        }
    }

    private List<Stage> selected(EnumerationFailed problems) throws IOException {
        int count;

        count = (stageClause != null ? 1 : 0) + (all ? 1 : 0);
        switch (count) {
            case 0:
                return defaultSelected(problems);
            case 1:
                if (all) {
                    return all(problems);
                } else if (stageClause != null) {
                    return session.list(problems, or(session.properties(), stageClause));
                } else {
                    throw new IllegalStateException();
                }
            default:
                throw new ArgumentException("too many select options");
        }
    }


    protected List<Stage> all(EnumerationFailed problems) throws IOException {
        return session.list(problems, new Predicate() {
            @Override
            public boolean matches(Stage stage) {
                return true;
            }
        });
    }

    protected Stage selected() throws IOException {
        String id;

        id = session.getSelectedStageId();
        if (id == null) {
            throw new IOException("no stage selected");
        }
        return session.load(id);
    }

    /** override this to change the default */
    protected List<Stage> defaultSelected(EnumerationFailed notUsed) throws IOException {
        return Collections.singletonList(selected());
    }

    /* Note that the stage is not locked when this method is called. @return true to use prefix stream. */
    public boolean doBefore(List<Stage> stages, int indent) throws IOException {
        return stages.size() != 1;
    }

    private String autoChown(Stage stage) throws Exception {
        String postChown;
        if (withAutoChowning() && (autoRechown || autoChown) && !stage.owner().equals(session.user)) {
            postChown = autoRechown ? stage.owner() : null;
            new Chown(session, true, session.user).doRun(stage);
        } else {
            postChown = null;
        }
        return postChown;
    }

    private boolean autoStart(Stage stage, Map<Info, Object> status) throws Exception {
        boolean postStart;
        if (withAutoRunning() && (autoRestart || autoStop) && stage.state() == Stage.State.UP) {
            postStart = autoRestart;
            Status.processStatus(processes(), stage, status);
            new Stop(session, false).doRun(stage);
        } else {
            postStart = false;
        }
        return postStart;
    }


    public final void doRun(Stage stage) throws Exception {
        doMain(stage);
        doFinish(stage);
    }

    /** main method to perform this command */
    public abstract void doMain(Stage stage) throws Exception;

    /** override this if your doMain method needs some finishing */
    public void doFinish(Stage stage) throws Exception {
    }

    //--

    /* Note that the stage is not locked when this method is called. */
    public void doAfter() throws Exception {
    }

    public enum Fail {
        NORMAL, AFTER, NEVER
    }


    //--

    private Predicate or(Map<String, Property> properties, String string) {
        final List<Predicate> ops;

        ops = new ArrayList<>();
        for (String op : Separator.COMMA.split(string)) {
            ops.add(and(properties, op));
        }
        return new Predicate() {
            @Override
            public boolean matches(Stage stage) throws IOException {
                for (Predicate op : ops) {
                    if (op.matches(stage)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    private static final Separator AND = Separator.on('+');

    private Predicate and(Map<String, Property> properties, String string) {
        final List<Predicate> ops;

        ops = new ArrayList<>();
        for (String op : AND.split(string)) {
            ops.add(compare(properties, op));
        }
        return new Predicate() {
            @Override
            public boolean matches(Stage stage) throws IOException {
                for (Predicate op : ops) {
                    if (!op.matches(stage)) {
                        return false;
                    }
                }
                return true;
            }
        };
    }


    private Predicate compare(final Map<String, Property> properties, final String string) {
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
                public boolean matches(Stage stage) throws IOException {
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
        try {
            field = Field.valueOf(name.toUpperCase());
            property = null;
        } catch (IllegalArgumentException e) {
            field = null;
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
                Map<Info, Object> status;
                boolean result;
                Object obj;
                String str;
                Property p;

                if (constField != null) {
                    status = Status.status(session, processes(), stage);
                    obj = status.get(constField);
                } else {
                    p = properties.get(constProperty);
                    if (p == null) {
                        throw new PredicateException("property or status field not found: " + constProperty);
                    }
                    obj = p.get(stage.config());
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
        private final Map<Stage, Start> postStarts;
        private final Map<Stage, String> postChowns;

        public Worker(int width, EnumerationFailed failures, boolean withPrefix) {
            this.width = width;
            this.failures = failures;
            this.withPrefix = withPrefix;
            this.postStarts = new LinkedHashMap<>();
            this.postChowns = new LinkedHashMap<>();
        }

        public void main(Stage stage) throws Exception {
            run(stage, true);
        }
        public void finish(Stage stage) throws Exception {
            run(stage, false);
        }

        private void run(Stage stage, boolean main) throws Exception {
            try (Lock lock1 = createLock(stage.backstageLock(), backstageLock);
                 Lock lock2 = createLock(stage.directoryLock(), directoryLock)) {
                if (withPrefix) {
                    ((PrefixWriter) console.info).setPrefix(Strings.padLeft("{" + stage.getName() + "} ", width));
                }
                session.logging.setStage(stage.getId(), stage.getName());

                if (main) {
                    runMain(stage);
                } else {
                    runFinish(stage);
                }
            } catch (ArgumentException e) {
                if (fail == Fail.NORMAL) {
                    throw e;
                }
                failures.add(stage.getBackstage(), e);
            } catch (Error | RuntimeException e) {
                console.error.println(stage.getName() + ": " + e.getMessage());
                throw e;
            } catch (Exception e) {
                if (fail == Fail.NORMAL) {
                    throw e;
                }
                failures.add(stage.getBackstage(), e);
            } finally {
                session.logging.setStage("", "");
                if (console.info instanceof PrefixWriter) {
                    ((PrefixWriter) console.info).setPrefix("");
                }
            }
        }

        private void runMain(Stage stage) throws Exception {
            Map<Info, Object> status;
            boolean debug;
            boolean suspend;

            console.verbose.println("*** stage main");
            status = new HashMap<>();
            if (autoStart(stage, status)) {
                debug = status.get(Field.DEBUGGER) != null;
                suspend = (Boolean) status.get(Field.SUSPEND);
                postStarts.put(stage, new Start(session, debug, suspend));
            }
            postChowns.put(stage, autoChown(stage));
            doMain(stage);
        }

        private void runFinish(Stage stage) throws Exception {
            String postChown;
            Start postStart;

            console.verbose.println("*** stage finish");
            doFinish(stage);
            postChown = postChowns.get(stage);
            if (postChown != null) {
                // do NOT call session.chown to get property locking
                new Chown(session, true, postChown).doRun(stage);
            }
            postStart = postStarts.get(stage);
            if (postStart != null) {
                if (!stage.getDirectory().exists()) {
                    // stage removed -- no need to start again
                    return;
                }
                postStart.doRun(stage);
            }
        }
    }
}
