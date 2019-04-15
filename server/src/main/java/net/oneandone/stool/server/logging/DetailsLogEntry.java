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
package net.oneandone.stool.server.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.Map;

public class DetailsLogEntry {
    public static DetailsLogEntry forEvent(ILoggingEvent event) {
        Map<String, String> mdc;

        mdc = event.getMDCPropertyMap();
        return new DetailsLogEntry(mdc.get("client-invocation"), event.getLevel().toString(), event.getMessage());
    }

    /** Count-part of the Logging.log method. */
    public static DetailsLogEntry parse(String line) {
        int len;

        int invocation;
        int level;

        len = line.length();
        invocation = line.indexOf('|');
        level = line.indexOf('|', invocation + 1);
        if (line.charAt(len - 1) != '\n') {
            throw new IllegalArgumentException(line);
        }

        return new DetailsLogEntry(
                line.substring(0, invocation),
                line.substring(invocation + 1, level),
                unescape(line.substring(level + 1, len -1)));
    }

    private static String unescape(String message) {
        StringBuilder builder;
        char c;
        int max;

        if (message.indexOf('\\') == -1) {
            return message;
        } else {
            max = message.length();
            builder = new StringBuilder(max);
            for (int i = 0; i < max; i++) {
                c = message.charAt(i);
                if (c != '\\') {
                    builder.append(c);
                } else {
                    i++;
                    c = message.charAt(i);
                    switch (c) {
                        case 'n':
                            builder.append('\n');
                            break;
                        case 'r':
                            builder.append('\r');
                            break;
                        default:
                            builder.append(c);
                            break;
                    }
                }
            }
            return builder.toString();
        }
    }

    //--

    public final String clientInvocation;
    public final String level;
    public final String message;

    public DetailsLogEntry(String clientInvocation, String level, String message) {
        this.clientInvocation = clientInvocation;
        this.level = level;
        this.message = message;
    }

    public String toString() {
        StringBuilder result;
        char c;

        result = new StringBuilder();
        result.append(clientInvocation).append('|');
        result.append(level).append('|');
        for (int i = 0, max = message.length(); i < max; i++) {
            c = message.charAt(i);
            switch (c) {
                case '\r':
                    result.append("\\r");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\\':
                    result.append("\\\\");
                    break;
                default:
                    result.append(c);
                    break;
            }
        }
        result.append('\n');
        return result.toString();
    }
}
