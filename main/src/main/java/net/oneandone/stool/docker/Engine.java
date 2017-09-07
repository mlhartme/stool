package net.oneandone.stool.docker;

import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.http.HttpFilesystem;
import net.oneandone.sushi.fs.http.HttpNode;

import javax.net.SocketFactory;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URISyntaxException;

public class Engine {
    public static void main(String[] args) throws IOException, URISyntaxException {
        System.out.println("version: " + Engine.create().version());
    }

    public static Engine create() throws IOException {
        World world;
        HttpFilesystem fs;
        HttpNode root;

        world = World.create();
        HttpFilesystem.wireLog("wire.log");
        fs = (HttpFilesystem) world.getFilesystem("http");
        fs.setSocketFactorySelector(Engine::unixSocketFactorySelector);
        root = (HttpNode) world.validNode("http://localhost/v1.30");
        root.getRoot().addExtraHeader("Content-Type", "application/json");
        return new Engine(root);
    }

    private final HttpNode root;

    public Engine(HttpNode root) {
        this.root = root;
    }


    public String version() throws IOException {
        return root.join("version").readString();
    }

    public static SocketFactory unixSocketFactorySelector(String protocol, String hostname) {
        return new SocketFactory() {
            @Override
            public Socket createSocket(String s, int i) throws IOException {
                return socket();
            }

            @Override
            public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) throws IOException {
                return socket();
            }

            @Override
            public Socket createSocket(InetAddress inetAddress, int i) throws IOException {
                return socket();
            }

            @Override
            public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) throws IOException {
                return socket();
            }

            private Socket socket() throws IOException {
                UnixSocketAddress address;

                address = new UnixSocketAddress(new File("/var/run/docker.sock"));
                return UnixSocketChannel.open(address).socket();
            }
        };
    }
}
