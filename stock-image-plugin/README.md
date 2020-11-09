# Image Maven Plugin

This is Maven plugin to build Docker images. It'a not intendented to run images (e.g. for testing).

## Shared builds

The main features of this plugin is *shared Docker builds*. Shared builds means that the Dockerfile (or more precisely:
the Docker build context including the Dockerfile) is *not* part of the respective Maven module or projects, i.e. it's not part of the source
tree and it's not specified inline in the pom (like the fabric8 plugin allows). Instead, the Docker build it loaded from a central, shared location.

We hope to benefit form Shared Docker build becuase
* simplify maintenance: we just have to updates a small number shared builds instead of a possibly hug number of projects
* separation: Java developers can concentrate on their Java build - they don't have to care about the best way to build an image for them,
  they simply reference the latest shared build that fits their framework/setup. Otherwise, many developers will probably copy-and-paste
  a Dockerfile and not keep it up-to-date
* operations: is much easier to keep a small number of different shared builds up and running. Otherwise, you have to check the particular
  build of every individual application.

Rational: shared builds is the reason I wrote this plugin; I didn't find a proper way to do this in other Maven Docker plugins, and I want
to encourage Java developers *not* to copy-paste a Docker build into their project.


## Parametrization

Builds can be parameterized with Dockerfile build arguments and files, both of them configured in the plugin configuratuion. Use build
arguments to configure things like the Java version. Use files to pass in artifacts.

## Links

Other Docker Maven Plugins I'm aware of:

* https://github.com/fabric8io/docker-maven-plugin
* https://github.com/spotify/docker-maven-plugin
* https://github.com/spotify/dockerfile-maven
