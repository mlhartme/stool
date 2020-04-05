# Stool development

Stool has a client- and a server part, both talk rest to each other. The server itself also tasks to the Docker daemon on the hosting machine.

To build Stool, you need:
* Linux or Mac
* Java 8+
* Maven 3+
* Git
* Docker (with api 1.38+) and Kubernetes. 
* TODO: depends on fault and cisotools

## Docker setup 

Docker on macOS should be fine out of the box (it sets up a Linux VM, and all docker containers run as the development user).

With Linux, you either setup Docker to run as your user or as a root user.

### Linux - Containers run as root

This is the normal setup: install Docker for you Linux distribution and make sure that the development user has permissions to access
the docker socket (usually be adding him/her to the Docker group).

In addition, you need to make sure that root has access to the Fault workspace:

* add `user_allow_other` to `/etc/fuse.conf`
* append `-Dfault.mount_opts=allow_root` to `FAULT_OPTS`

TODO: that's all to make it work?


### Linux - Containers run as development user

Running Containers as a none-root user is more secure and saves some trouble when Stool Server needs access to the Fault Workspace.
To configure this you have to a) execute containers as the development user, and b) adjust docker socket permissions that
are accessible from containers (which is needed for Stool server).

* edit `/lib/systemd/system/docker.socket` and change `group` entry to the primary group of your development user
  (note that this can't be done in `/etc/docker/daemon.json` because systemd sets up the socket)
* setup user ns mapping for your Docker daemon to run all containers as your development user.
  This is a common security measure and, it makes sure that files in bind mounted directories are creates with normal development 
  user and group. To do so: 
  (replace <user> with your developer user (e.g. mlhartme), <group> with its primary group (usually the same as <user>, 
  <uid> with your developer user id (e.g. 1000), and gid with your development user's primary group id (e.g. 1000); 
  run `id` to see your values)
  * /etc/docker/daemon.json
 
        {
          "userns-remap": "<user>:<group>"
        }
        
  * /etc/subuid

        <user>:<uid>:1
        <user>:100000:65535

  * /etc/subgid

        <group>:<gid>:1
        <group>:100000:65535


Apply changes with

    systemctl daemon-reload
    systemctl restart docker

Check if `/var/run/docker.sock` is created with the correct primary group. Also check 
`journalctl -u docker` for errors. Note that the following warnings seem to be ok for Stool (i've seen them on Debian and Majaro):

    Your kernal does not support cgroup rt period
    Your kernel does not support cgroup rt runtime

Next, run `docker version`, you should get both the server and the client version. If Docker reports a permission problem, check
the above `group` configuration for `/var/run/docker.sock`. 

Finally, make sure you have a `stool` network, it's needed for tests and to run the server:

    docker network create stool


   
## Building

Get Stool sources with

    git clone https://github.com/mlhartme/stool.git
    
Stool has a multi-module Maven build with a server and a client module. Build it with

    cd stool
    mvn clean install

Notes:
* if javadoc generation fails: try to set the JAVA_HOME environment variable to work-around a 
  Javadoc Plugin 3.1.1 [bug](https://issues.apache.org/jira/browse/MJAVADOC-595)))

    
The server build results in a Docker image, you can see it with `docker image ls`
The client you just built is `client/target/stool`. Add it to your path and make sure that 

    stool -v version
    
print the correct build date.

Next, setup client and server by running 

    stool setup

and make sure to enable `localhost` as a server. Follow the instruction of the `setup` command, in particular, source 
`shell.inc` and define an sserver aliases in your shell startup (e.g `~/.bashrc`). Now start the server with

    sserver up
    
Point you browser to http://localhost:31000 to see the dashboard. You can now create stages on your server as described in the 
[Documentation](https://github.com/mlhartme/stool/blob/master/client/documentation.md)


## Notes
 
Maven releases go to Sonatype, you need the respective account. After running `mvn release:prepare` and `mvn release:perform`, go to
the staging repository and promote the release.

## Multi Threading

The client part is single threaded, no need to synchronize.
The server part is multithreaded. TODO engine.world vs server.world
