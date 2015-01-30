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

    private DateTime date;

    public Until() {
        date = DateTime.now().plusDays(7);
    }
    public static Until reserved() {
        Until until;

        until = new Until();
        until.date = null;

        return until;
    }
    public static Until withOffset(int days) {
        Until until;

        until = new Until();
        until.date = DateTime.now().plusDays(days);

        return until;

    }
    public static Until fromString(String input) {
        Until until;
        until = new Until();
        if (null == input || input.equals("null") || input.equals("") || input.equals("reserved") || input.equals("expired")) {
            until.date = null;
        } else {
            until.date = DateTimeParser.create().parse(input);
        }

        return until;
    }

    public static Until fromHuman(String input) {
        Until until;
        until = new Until();
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
