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

import net.oneandone.stool.configuration.Property;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Lock;
import net.oneandone.stool.util.Predicate;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.io.PrefixWriter;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class StageCommand extends SessionCommand {
    @Option("stage")
    private String stageClause;

    @Option("all")
    private boolean all;

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
                session.logging.setStage(stage.config().id, stage.getName());
                doInvoke(stage);
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
                    return session.list(problems, or(StageConfiguration.properties(session.extensionsFactory), stageClause));
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

    /* @return true to use prefix stream */
    public boolean doBefore(List<Stage> stages, int indent) throws IOException {
        return stages.size() != 1;
    }

    public abstract void doInvoke(Stage stage) throws Exception;

    //--
    public void doAfter() throws Exception {
    }

    public static enum Fail {
        NORMAL, AFTER, NEVER
    }


    //--

    private static Predicate or(Map<String, Property> properties, String string) {
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

    private static Predicate and(Map<String, Property> properties, String string) {
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


    private static Predicate compare(final Map<String, Property> properties, final String string) {
        int idx;
        String name;
        final boolean eq;
        Status.Field field;
        final Status.Field constField;
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
            field = Status.Field.valueOf(name.toUpperCase());
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
                Map<Status.Field, Object> status;
                boolean result;
                Object obj;
                String str;

                if (constField != null) {
                    status = Status.status(stage);
                    obj = status.get(constField);
                } else {
                    obj = properties.get(constProperty).get(stage.config());
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
}
