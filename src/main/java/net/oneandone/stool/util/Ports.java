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

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

public class Ports {
    public static Ports forName(String name, Ports first, Ports last) {
        return new Ports((Math.abs(name.hashCode()) % (last.prefix - first.prefix + 1)) + first.prefix);
    }

    // TODO: private
    public final int prefix;

    public Ports(int prefix) {
        this.prefix = prefix;
    }

    public int prefix() {
        return prefix;
    }

    public Ports next() {
        return new Ports(prefix + 1);
    }

    public int tomcatHttp() {
        return prefix * 10;
    }

    public int tomcatStop() {
        return prefix * 10 + 1;
    }

    public int tomcatHttps() {
        return prefix * 10 + 3;
    }

    public int debugPort() {
        return prefix * 10 + 5;
    }

    public int jmx() {
        return prefix * 10 + 6;
    }

    //--

    public String toString() {
        return Integer.toString(prefix);
    }

    public int hashCode() {
        return prefix;
    }

    public boolean equals(Object obj) {
        if (obj instanceof Ports) {
            return prefix == ((Ports) obj).prefix;
        }
        return false;
    }

    public boolean within(Ports first, Ports last) {
        return prefix >= first.prefix && prefix <= last.prefix;
    }

    //--
    /**
     * See http://stackoverflow.com/questions/434718/sockets-discover-port-availability-using-java
     */
    public void checkFree() throws IOException {
        boolean available;
        ServerSocket socket;

        // convert portPrefix (three digits) into a proper port (add fourth digit aka suffix)
        List<Integer> portsToCheck = new ArrayList<>();
        portsToCheck.add(jmx());
        portsToCheck.add(debugPort());
        portsToCheck.add(tomcatStop());
        portsToCheck.add(tomcatHttp());
        portsToCheck.add(tomcatHttps());

        for (int portNumber : portsToCheck) {
            socket = null;
            available = false;
            try {
                socket = new ServerSocket(portNumber);
                available = true;
            } catch (IOException e) {
                break;
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
                throw new IOException("portPrefix already in used: " + prefix);
            }
        }
    }

    // TODO: for stool config ...
    public static class PortsTypeAdapter extends TypeAdapter<Ports> {
        @Override
        public void write(JsonWriter out, Ports value) throws IOException {
            out.value(value.prefix);
        }

        @Override
        public Ports read(JsonReader in) throws IOException {
            return new Ports(in.nextInt());
        }
    }
}
