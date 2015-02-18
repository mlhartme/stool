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
package net.oneandone.stool;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.Appender;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Lock;
import net.oneandone.stool.util.Predicate;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.fs.ModeException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.PrefixWriter;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class StageCommand extends SessionCommand {
    protected long start;
    protected long end;

    @Option("stage")
    private String stageNames;

    @Option("all")
    private boolean all;

    @Option("all-state")
    private String allState;

    @Option("all-owner")
    private String allOwner;

    @Option("fail")
    private Fail fail = Fail.NORMAL;

    public StageCommand(Session session) {
        super(session);
    }

    @Override
    protected Lock lock() {
        return null;

    }
    protected Lock stageLock(Stage stage) {
        return new Lock(session.user, stage.shared().join("stage.aquire"));
    }

    @Override
    public void doInvoke() throws Exception {
        Lock lock;
        int width;
        List<Stage> lst;
        EnumerationFailed failures;
        String failureMessage;
        boolean withPrefix;

        failures = new EnumerationFailed();
        lst = selected(failures);
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
        for (Stage stage : lst) {
            console.verbose.println("current stage: " + stage.getName());
            if (noLock) {
                lock = null;
            } else {
                lock = stageLock(stage);
            }
            if (lock != null) {
                lock.aquire(getClass().getSimpleName().toLowerCase(), console);
            }
            try {
                if (withPrefix) {
                    ((PrefixWriter) console.info).setPrefix(Strings.padLeft("{" + stage.getName() + "} ", width));
                }
                addStageAppenders(stage);
                doInvoke(stage);
            } catch (Error | RuntimeException e) {
                console.error.println(stage.getName() + ": " + e.getMessage());
                throw e;
            } catch (Exception e) {
                if (fail == Fail.NORMAL) {
                    throw e;
                }
                failures.add(stage.getWrapper(), e);
            } finally {
                removeStageAppenders();
                if (console.info instanceof PrefixWriter) {
                    ((PrefixWriter) console.info).setPrefix("");
                }
                if (lock != null) {
                    lock.release();
                }
            }
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
    private void addStageAppenders(Stage stage) throws IOException {
        addStageAppender(stage, "IN").info(session.command);
        addStageAppender(stage, "OUT");
        addStageAppender(stage, "ERR");
    }

    private Logger addStageAppender(Stage stage, String name) throws IOException {
        Logger logger;

        logger = session.logging.lookup(name);
        logger.addAppender(session.logging.stageAppender(stage.getWrapper().join("stage.log"), name));
        return logger;
    }

    private void removeStageAppenders() {
        removeStageAppender("IN");
        removeStageAppender("OUT");
        removeStageAppender("ERR");
    }

    private void removeStageAppender(String name) {
        Logger logger;
        Appender appender;

        logger = session.logging.lookup(name);
        appender = logger.getAppender("stageAppender");
        logger.detachAppender(appender);
        appender.stop();
    }

    private List<Stage> selected(EnumerationFailed problems) throws IOException {
        int count;
        final Stage.State s;

        count = (stageNames != null ? 1 : 0) + (all ? 1 : 0) + (allOwner != null ? 1 : 0) + (allState != null ? 1 : 0);
        switch (count) {
            case 0:
                return defaultSelected(problems);
            case 1:
                if (all) {
                    return all(problems);
                } else if (allOwner != null) {
                    return session.list(problems, new Predicate() {
                        @Override
                        public boolean matches(Stage stage) throws ModeException {
                            return stage.technicalOwner().equals(allOwner);
                        }
                    });
                } else if (allState != null) {
                    s = Stage.State.valueOf(allState.toUpperCase());
                    return session.list(problems, new Predicate() {
                        @Override
                        public boolean matches(Stage stage) throws IOException {
                            return s.equals(stage.state());
                        }
                    });
                } else if (stageNames != null) {
                    return explicit(stageNames);
                } else {
                    throw new IllegalStateException();
                }
            default:
                throw new ArgumentException("too many select options");
        }
    }

    private List<Stage> explicit(String names) throws IOException {
        FileNode wrapper;
        List<Stage> result;

        result = new ArrayList<>();
        for (String name : Separator.COMMA.split(names)) {
            wrapper = session.wrappers.join(name);
            if (!wrapper.isDirectory()) {
                throw new ArgumentException("no such stage: " + name);
            }
            result.add(Stage.load(session, wrapper));
        }
        if (result.isEmpty()) {
            throw new ArgumentException("empty stage name: " + names);
        }
        return result;
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
        String name;

        name = session.getSelectedStageName();
        if (name == null) {
            throw new IOException("no stage selected - run 'stool select myStageName'.");
        }
        return session.load(name);
    }

    /** override this to change the default */
    protected List<Stage> defaultSelected(EnumerationFailed notUsed) throws IOException {
        return Collections.singletonList(selected());
    }

    protected void timeStart() {
        start = System.currentTimeMillis();
    }

    protected void timeEnd() {
        end = System.currentTimeMillis();
    }
    protected long executionTime() {
        return end - start;
    }
    /* @return true to use prefix stream */
    public boolean doBefore(List<Stage> stages, int indent) throws IOException {
        return stages.size() != 1;
    }

    public abstract void doInvoke(Stage s) throws Exception;

    //--
    public void doAfter() throws IOException {
    }

    public static enum Fail {
        NORMAL, AFTER, NEVER
    }

}
