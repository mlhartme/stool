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
package net.oneandone.stool.util;

import net.oneandone.inline.ArgumentException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class Expire {
    private static final String NEVER = "never";

    public static Expire fromNumber(int n) {
        // integers are valid inputs ...
        return fromString(Integer.toString(n));
    }

    public static Expire fromString(String input) {
        int number;
        LocalDate date;

        if (input.equals(NEVER)) {
            date = null;
        } else {
            try {
                number = Integer.parseInt(input);
                date = number == 0 ? null : LocalDate.now().plusDays(number);
            } catch (NumberFormatException e) {
                try {
                    date = LocalDate.parse(input, FORMAT);
                } catch (DateTimeParseException e2) {
                    throw new ArgumentException("invalid date. Expected format: " + FORMAT.toString() + ", got " + input);
                }
            }
        }
        return new Expire(date);
    }

    //--

    /** null stands for 'never' */
    private final LocalDate date;

    public Expire(LocalDate date) {
        this.date = date;
    }

    public boolean isExpired() {
        return expiredDays() > 0;
    }

    /** @return negative for not expired, 0 last day */
    public int expiredDays() {
        if (date == null) {
            return Integer.MIN_VALUE;
        }
        return date.until(LocalDate.now()).getDays();
    }

    @Override
    public String toString() {
        if (date == null) {
            return NEVER;
        } else {
            return FORMAT.format(date);
        }
    }

    public boolean equals(Object obj) {
        if (obj instanceof Expire expire) {
            if (date == null) {
                return expire.date == null;
            } else {
                return date.equals(expire.date);
            }
        }
        return false;
    }

    public int hashCode() {
        return date == null ? 0 : date.hashCode();
    }

    //--

    private static final DateTimeFormatter FORMAT =  DateTimeFormatter.ofPattern("yyyy-MM-dd");
}
