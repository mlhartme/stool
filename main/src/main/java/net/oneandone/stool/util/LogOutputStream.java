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
package net.oneandone.stool.util;

import java.io.OutputStream;

public class LogOutputStream extends OutputStream {
    private final Logging logging;
    private final String logger;
    private final StringBuilder mem;

    public LogOutputStream(Logging logging, String logger) {
        this.logging = logging;
        this.logger = logger;
        this.mem = new StringBuilder();
    }

    public void write(int b) {
        if (b == 10) {
            writeLine();
        } else {
            mem.append((char) b);
        }
    }

    private void writeLine() {
        logging.log(logger, mem.toString());
        mem.setLength(0);
    }

    public void flush() {
    }
}
