package net.oneandone.stool.util;

import net.oneandone.sushi.util.Separator;

import java.util.ArrayList;
import java.util.List;

public class MultiString {
    private static final Separator SEP = Separator.on('|').trim();

    public final List<String> lst;

    public MultiString() {
        lst = new ArrayList<>();
        lst.add("");
    }

    public void append(String str) {
        int prev;
        int open;
        int close;
        List<String> tmp;

        prev = 0;
        while (true) {
            open = str.indexOf('(', prev);
            if (open == -1) {
                appendAll(str.substring(prev, str.length()));
                return;
            }
            open++;
            close = str.indexOf(')', open);
            if (close == -1) {
                throw new IllegalArgumentException("closing ) not found: " + str);
            }
            tmp = new ArrayList<>(lst);
            lst.clear();
            for (String p : SEP.split(str.substring(open, close))) {
                for (String a : tmp) {
                    lst.add(a + p);
                }
            }
            prev = close + 1;
        }
    }

    private void appendAll(String str) {
        for (int i = 0; i < lst.size(); i++) {
            lst.set(i, lst.get(i) + str);
        }
    }

    public boolean contains(String str) {
        return lst.contains(str);
    }
}
