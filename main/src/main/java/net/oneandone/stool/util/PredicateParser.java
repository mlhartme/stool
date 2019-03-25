package net.oneandone.stool.util;

import net.oneandone.stool.net.oneandone.stool.client.cli.PredicateException;
import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.util.List;

public class PredicateParser {
    public static Predicate parse(String stringOpt) {
        List<String> args;

        if (stringOpt == null) {
            return new Predicate() {
                @Override
                public boolean matches(Stage stage) {
                    return true;
                }
            };
        } else {
            args = Separator.COMMA.split(stringOpt);
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
    }

    private static final Separator AND = Separator.on('+');

    private static Predicate and(String string) {
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


    private static Predicate compare(Stage stage, final String string) throws IOException {
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
}
