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

import org.slf4j.Logger;

import java.io.OutputStream;

public class Slf4jOutputStream extends OutputStream {
    private Logger logger;
    private StringBuilder mem;
    private boolean logAsError;


    public Slf4jOutputStream(Logger logger, boolean logAsError) {
        setLogger(logger);
        setLogAsError(logAsError);
        mem = new StringBuilder();
    }

    public void setLogAsError(boolean error) {
        this.logAsError = error;
    }


    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public Logger getLogger() {
        return logger;
    }


    public void write(int b) {
        if (b == 10) {
            writeLine();
        } else {
            mem.append((char) b);
        }
    }

    public boolean getLogAsError() {
        return logAsError;
    }

    private void writeLine() {
        if (getLogAsError()) {
            logger.error(mem.toString());
        } else {
            logger.info(mem.toString());
        }
        mem.setLength(0);

    }

    public void flush() {
    }
}
