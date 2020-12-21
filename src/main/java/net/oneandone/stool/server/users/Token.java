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
package net.oneandone.stool.server.users;

import net.oneandone.stool.server.HistoryEntry;

import java.time.LocalDateTime;
import java.util.Random;

public class Token {
    private static final String SEPARATOR = " @ ";

    public final String value;
    public final LocalDateTime created;

    public static Token generate(Random random) {
        return new Token(
                Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong()));
    }

    public static Token fromString(String str) {
        int idx;

        idx = str.indexOf(SEPARATOR);
        if (idx == -1) {
            throw new IllegalArgumentException("invalid token: " + str);
        }
        return new Token(str.substring(0, idx), LocalDateTime.parse(str.substring(idx + SEPARATOR.length()), HistoryEntry.DATE_FMT));
    }

    public Token(String value) {
        this(value, LocalDateTime.now());
    }

    public Token(String value, LocalDateTime created) {
        this.value = value;
        this.created = created;
    }

    public String toString() {
        return value + SEPARATOR + HistoryEntry.DATE_FMT.format(created);
    }

    public int hashCode() {
        return value.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof Token) {
            return value.equals(((Token) obj).value);
        } else {
            return false;
        }
    }
}
