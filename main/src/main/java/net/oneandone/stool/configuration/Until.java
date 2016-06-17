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
package net.oneandone.stool.configuration;

import net.oneandone.inline.ArgumentException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class Until {
    public static Until reserved() {
        return new Until(null);
    }

    public static Until withDefaultOffset() {
        return withOffset(7);
    }

    public static Until withOffset(int days) {
        return new Until(LocalDate.now().plusDays(days));
    }

    public static Until fromString(String input) {
        if (input.equals("reserved")) {
            return Until.reserved();
        } else {
            return new Until(parse(input));
        }
    }

    public static Until fromHuman(String input) {
        if (null == input) {
            throw new IllegalArgumentException();
        }
        if (input.equals("reserved")) {
            return new Until(null);
        } else {
            return new Until(parse(input));
        }
    }

    //--

    /** null stands for 'reserved' */
    private final LocalDate date;

    public Until(LocalDate date) {
        this.date = date;
    }

    public boolean isReserved() {
        return date == null;
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
        if (isReserved()) {
            return "reserved";
        } else {
            return FORMAT.format(date);
        }
    }

    //--

    private static final DateTimeFormatter FORMAT =  DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static LocalDate parse(String input) {
        try {
            return LocalDate.parse(input, FORMAT);
        } catch (DateTimeParseException e) {
            throw new ArgumentException("invalid date. Expected format: " + FORMAT.toString() + ", got " + input);
        }
    }
}
