# Stool development

To build Stool, you need:
* Linux or Mac
* Java 8+
* Maven 3+
* Git
* Docker (with api 1.38+) including docker-compose. 


## Docker checks

Make sure you can invoke Docker without sudo.

Check journalctl -u docker for error. Note that the following warnings seem to be ok for Stool (i've seen them on Debian and Majaro):

    Your kernal does not support cgroup rt period
    Your kernel does not support cgroup rt runtime


## Building

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
