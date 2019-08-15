# Stool development

To build Stool, you need:
* Linux or Mac
* Java 8+
* Maven 3+
* Git
* Docker (with api 1.38+) including docker-compose. 
* TODO: probably depends on cisotools

## Docker setup 

Docker on macOS should be fine out of the box (it sets up a Linux VM, and all docker containers run as the current user).

### Linux

Docker on Linux needs some permission tweaking to a) execute containers the a normal user and b) give containers access to the docker socket.

The common way to give a normal user access to the docker socket is to add him to the docker group. Stool needs a different setup (TODO more on that):

* edit /lib/systemd/system/docker.socket and change the group to the primary group of your user
  (this can't be done in /etc/docker/daemon.json because systemd sets up the socket)
* setup user ns mapping for your Docker daemon (otherwise, bind mounds would create root files). 
  Replace name with your user name (e.g. mlhartme), uid with your user id (e.g. 1000), and gid with your docker group id (e.g. 998)

* /etc/docker/daemon.json

    {
      "userns-remap": "stool:docker",
    }

* /etc/subuid

    name:uid:1
    name:100000:65535

* /etc/subgid

    docker:gid:1
    docker:100000:65535


Make sure you can invoke Docker without sudo.

Check journalctl -u docker for error. Note that the following warnings seem to be ok for Stool (i've seen them on Debian and Majaro):

    Your kernal does not support cgroup rt period
    Your kernel does not support cgroup rt runtime


Make sure you have a "stool" network before you run the tests:

    docker network create stool


   
## Building

Maven's Javadoc Plugin 3.1.1 has a problem with Debian's Openjdk 11 package, you need to explicitly set JAVA_HOME. Otherwise,
the javadoc executable cannot be located.

To get started, checkout the source code with

    git clone https://github.com/mlhartme/stool.git

and build with

    mvn clean install
    
If you see failing tests, check for Docker problems with

    docker info
    
You should get both the client and the server version. If Docker reports a permission problem, setup the respective `docker` 
group and restart the whole machine to make sure all processes and shells see the permission change.

Stool has a multi-module build with a server and a client module. The client you just built is `client/target/stool`.
Add it to your path. The server build results in a Docker image, you can see it with `docker image ls`

Test the client with 

    stool -v version
    
You should get the current version with a date that's just now.

Next, setup client and server by running 

    stool setup

and make sure to enable `localhost` as a server. Follow the instruction of the `setup` command, in particular, include 
`shell.inc` in your server setup and define server aliases. Next, adjust `~/.stool/server.yml` and run 

    sserver up
    
to start you server. Point you browser to http://localhost:9000 to see the dashboard. You can now create stages on your server.


## Notes
 
Releases go to Sonatype, you need the respective account. After running `mvn release:prepare` and `mvn release:perform`, go to
the staging repository and promote the release.
