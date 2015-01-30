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
package net.oneandone.stool.util.parser;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.LinkedHashSet;
import java.util.Set;


public class DateTimeParser implements Parser<DateTime> {
    public static DateTimeParser create() {
        Set<DateTimeFormatter> pattern;

        pattern = new LinkedHashSet<>();
        pattern.add(DateTimeFormat.forPattern("yyyy-MM-dd"));
        pattern.add(DateTimeFormat.forPattern("yyyy-MM-dd H:mm"));
        pattern.add(DateTimeFormat.forPattern("H:mm"));
        return new DateTimeParser(pattern);
    }

    private Set<DateTimeFormatter> patterns;

    public DateTimeParser(Set<DateTimeFormatter> pattern) {
        this.patterns = pattern;
    }

    public DateTime parse(String input) {
        DateTime result;

        for (DateTimeFormatter pattern : patterns) {
            result = parse(input, pattern);

            if (null != result) {
                if (result.getYear() == 1970) {
                    result = result.withYear(DateTime.now().getYear())
                      .withMonthOfYear(DateTime.now().getMonthOfYear())
                      .withDayOfMonth(DateTime.now().getDayOfMonth());
                }
                return result;
            }
        }

        return null;
    }

    private DateTime parse(String input, DateTimeFormatter pattern) {
        try {
            return DateTime.parse(input, pattern);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
