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

import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

public class Pool {
    private final int first;
    private final int last;
    private final int overview;

    private final FileNode wrappers;
    private List<Integer> lazyUsed;

    public Pool(int first, int last, int overview, FileNode wrappers) {
        if (first % 2 != 0) {
            throw new IllegalArgumentException("even port expected: " + first);
        }
        if (last % 2 != 1) {
            throw new IllegalArgumentException("odd port expected: " + last);
        }
        this.first = first;
        this.last = last;
        this.overview = overview;
        this.wrappers = wrappers;
        this.lazyUsed = null;
    }

    public int allocate(String name) throws IOException {
        return allocate(forName(name));
    }

    public int allocate(int start) throws IOException {
        int current;

        if ((start < first) || (start > last)) {
            throw new IllegalArgumentException("ports out of range: " + start);
        }
        if (start % 2 != 0) {
            throw new IllegalArgumentException("even port expected: " + start);
        }
        current = start;
        do {
            if (!used().contains(current)) {
                checkFree(current);
                checkFree(current + 1);
                lazyUsed.add(current);
                return current;
            }
            current += 2;
            if (current > last) {
                current = first;
            }
        } while (current != start);
        throw new IOException("cannot allocate port");
    }

    public int forName(String name) {
        return ((Math.abs(name.hashCode()) % (last - first + 1)) + first) & 0xfffffffe;
    }

    public List<Integer> used() throws IOException {
        if (lazyUsed == null) {
            lazyUsed = new ArrayList<>();
            lazyUsed.add(overview);
            for (FileNode wrapper : wrappers.list()) {
                Ports.addUsed(wrapper, lazyUsed);
            }
        }
        return lazyUsed;
    }

    /**
     * See http://stackoverflow.com/questions/434718/sockets-discover-port-availability-using-java
     */
    public static void checkFree(int port) throws IOException {
        boolean available;
        ServerSocket socket;

        socket = null;
        available = false;
        try {
            socket = new ServerSocket(port);
            available = true;
        } catch (IOException e) {
            // fall-through
        } finally {
            // Clean up
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    /* should not be thrown */
                    e.printStackTrace();
                }
            }
        }
        if (!available) {
            throw new IOException("port already in use: " + port);
        }
    }
}
