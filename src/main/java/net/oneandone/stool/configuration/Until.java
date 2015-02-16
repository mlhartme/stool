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

import net.oneandone.stool.util.parser.DateTimeParser;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Period;
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
        Until until;

        until = Until.withDefaultOffset();
        if (null == input || input.equals("null") || input.equals("") || input.equals("reserved") || input.equals("expired")) {
            Until.reserved();
        } else {
            new Until(DateTimeParser.create().parse(input));
        }
        return until;
    }

    public static Until fromHuman(String input) {
        Until until;

        until = Until.withDefaultOffset();
        if (null == input || input.equals("null") || input.equals("")) {
            throw new IllegalArgumentException("Please provide a valid date. For example "
              + new DateTime().plus(Days.days(10)).toString("yyyy-MM-dd"));
        } else if (input.equals("reserved")) {
            until.date = null;
        } else {
            until.date = DateTimeParser.create().parse(input);
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
        }
        return date.toString("yyyy-MM-dd HH:mm");
    }
}
