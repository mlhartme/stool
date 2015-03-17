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

import net.oneandone.sushi.cli.ArgumentException;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class Until {
    public static Until reserved() {
        return new Until(null);
    }

    public static Until withDefaultOffset() {
        return withOffset(7);
    }

    public static Until withOffset(int days) {
        return new Until(DateTime.now().plusDays(days));
    }

    public static Until fromString(String input) {
        if (input.equals("reserved")) {
            return Until.reserved();
        } else {
            return new Until(parse(input));
        }
    }

    public static Until fromHuman(String input) {
        Until until;

        until = Until.withDefaultOffset();
        if (null == input) {
            throw new IllegalArgumentException();
        }
        if (input.equals("reserved")) {
            until.date = null;
        } else {
            until.date = parse(input);
        }
        return until;
    }

    //--

    /** null stands for 'reserved' */
    private DateTime date;

    public Until(DateTime date) {
        this.date = date;
    }

    public boolean expired() {
        return date != null && DateTime.now().isAfter(date);
    }

    public boolean isReserved() {
        return date == null;
    }


    public boolean isBefore(int days) {
        return date != null && date.isBefore(DateTime.now().minus(Period.days(days)));

    }

    @Override
    public String toString() {
        if (isReserved()) {
            return "reserved";
        } else {
            return date.toString(LONG_FORMAT);
        }
    }

    //--

    private static final String SHORT_PATTERN = "yyyy-MM-dd";
    private static final String LONG_PATTERN = "yyyy-MM-dd H:mm";
    private static final DateTimeFormatter SHORT_FORMAT =  DateTimeFormat.forPattern(SHORT_PATTERN);
    private static final DateTimeFormatter LONG_FORMAT = DateTimeFormat.forPattern(LONG_PATTERN);

    private static synchronized DateTime parse(String input) {
        try {
            return DateTime.parse(input, LONG_FORMAT);
        } catch (IllegalArgumentException e) {
            // fall-through
        }
        try {
            return DateTime.parse(input, SHORT_FORMAT);
        } catch (IllegalArgumentException e) {
            // fall-through
        }
        throw new ArgumentException("invalid date. Expected format: " + LONG_PATTERN + " or " + SHORT_PATTERN + ", got " + input);
    }
}
