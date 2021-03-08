## Changelog 

### 7.0.0 (pending)

Stool 7 stages are managed with Helm and defined by helm charts
* Kubernetes resources for a stage are now defined by a Helm chart; this replaced the former hard-wired API calls
* directions define values for Helm charts
  * directly with template expressions (freemarker, with built-in scripts, e.g. to fetch vault secrets or generate credentials)
  * indirectly by extending base directions
* `sc create <stage> <directions>` installs a Helm chart with the values as defined by the directions, resulting in a Helm release
* `sc delete <stage>` uninstalls this release
* `start` and `stop` commands are gone - create results in a running stage, delete stops the stage first;
  you can "emulate" a stopped stage by settings replicas to 0
* stage properties are now called variables - they are Helm chart variables, managed with `sc config`; 
  charts can have arbitrary variables, Stool relies on some particular: `metadataNotify`, `metadataComment`, and 
  `metadataExpire`
* dumped disk quota handling; I might use Kubernetes ephemeral quotas later
* dumped memory quota handling, Kubernetes takes care of that

Helm like cli:
* `create`/`publish` command line arguments are similar to Helm's install/upgrade arguments: `sc create <name> <directions>`
* stage commands now take an explicit stage argument, e.g. `sc status hellowar`; 
  like before, you can specify predicated or `%all` instead of a fixed name
* dumped implicit workspaces derived from current director
  * provided explicit workspaces instead: `attach` and `detatch` now take an explicit named workspace argument 
    (referenced with '@' *name*) instead
  * dumped the stage indicator

Other changes:
* merged client and server
  * all functionality is in `sc` now; use `sc server` to start a server
  * running a server is optional now, Stool can talk to arbitrary Kubernetes contexts as well
  * settings.yaml now also contains server configuration
* image handling changes
  * dumped `sc build`, configure an image build in your Maven build instead
  * created a separate `maven-dockerbuild-plugin` with the former `sc build` functionality
  * Stool no longer wipes images
  * dumped jmxmp/5555, rely on readyness probes instead; also dumped `heap` field
* added `urlSubdomains`
* dumped fault support, use chart scripts instead
* dumped notify markers `@created-by` and `@last-modified-by`, they are too fragile. Use email adresses or login names instead
* `history`
  * dumped `-max` and `-details` options 
  * use `-v` to get more details
* `status`, `list`:  
  * added origin field, shown by default in `list`
  * added `available` field
  * dumped `origin-scm` field, check `sc images` instead
  * dumped `pod` field, that's too low-level
  * dumped `running` field, use `config image` instead
  * renamed `uptime` field to `last-deployed`, it now reports the corresponding Helm status
  * dumped `created-at` and `created-by`; added `first-deployed`
  * dumped `last-modified-at`, and `last-modified-by`; use `last-deployed` or check the history instead
  * dumped `images` field because it's very slow and deals to the registry, not kubernetes; 
    use `sc images` instead
  * added `directions` field, but it's hidden unless you explicitly reference this field
  * changed `images` command, it takes a repository argument instead of a stage now, and it displays generic labels only
* SC_HOME replaces SC_YAML to configure the location of configuration files; sc.yaml is now $SC_HOME/settings.yaml
* settings
  * introducted `local` and `proxy` section
  * added `librarypath`
  * replaced `registryPrefix` by `registryCredentials`
  * dumped `defaultExpire` and `defaultContact`  
  * `environment` now configures the environment accessed with the env template function
* log validation report result
* readiness probe for Stool server
* implementation changes
  * Maven: merged all modules into one
  * no longer use application files, use springboot instead  
  * dependency updates
    * sushi 3.2.2 to 3.3.0
    * fabric8 kubernetes client 4.10.2 to 5.0.0  
    * spring 5.2.9 to 5.3.2, springboot version 2.3.4 to 2.4.1, spring security 5.3.5 to 5.4.1
    * thymeleaf 3.0.11 to 3.0.12  
    * jquery 3.4.1 to 3.5.1, bootstrap 4.4.1 to 4.5.3  
    * junit 5.7.0


### 6.0.0 (2020-10-12)

Stool 6 switches from Docker hosts to Kubernetes: Stool server itself executes in Kubernetes, and it talks to Kubernetes 
to manage stages.

A *stage* in Stool 6 holds exactly one *app*. To deal with multiple apps in multi module projects, it provides *workspaces* to 
create/attach multiple stages with a singlle command. 

User-visible ports are gone (along with port options), dispatching to stages is now based on vhosts.

Dumped `disk-used` field.

Client changes
* general note: many user-visible changes make Stool 5 and 6 usable in parallel
* renamed `stool` to `sc`
  * `sc` stands for stool client (I chose this name because it's similar to `oc` and the `kc` alias I use)
  * raised minimal Java version from 8 to 11
  * global options
    * added `-context` to override the current context for this invocation
    * dumped `-project`; to configure the current project/workspace, invoke Stool with the respective current working directory.
* `setup`
  * generates the client configuration by creating a `.sc.yaml` file. The location of this file is configurable with
    the `SC_YAML` environment variable, `STOOL_HOME` is no longer supported. 
  * shell completion and stage indicator code is now generated by `sc shell-inc`.
    * changed the stage indicator to `> name <` to distinguish it from Stool 5
  * `.sc.yaml` is mostly a list of contexts; contexts have replaced servers, a context points to a Stool server and it stores
    the respective token to access it. 
  * enabling/disabling contexts is no longer possible, instead, you can configure a current context with
    the new `context` command.
  * moved server setup stuff into a separate `server` command
* `.backstage` directory
  * changed `.backstage` from a json file that stores the *project* configuration to a directory containing a `workspace.yaml` file
    holding the *workspace* configuration. 
* added `server` command to generate Kubernetes manifests for Stool server 
* added `context` command to manage a current context; you can now reference stages without a server name, it's 
  picked from the current context instead.
* dumped svn support
* `create`
  * checks for wars or docker sources and adds all matches to the project
  * name may contain an underscore `_` that will be replaced by the `_app` argument
* `delete`
  * replaces former `remove` command to follow Kubernetes terminology; 
  * it's a stage command now
* `auth` now applies to the current context, the explicit server argument is gone
* `attach`
  * with optional path
* `detach` is a stage command now
* `port-forward`
  * replaces former `tunnel` command
  * added `-timeout` argument
* `ssh`
  * added `-timeout` argument
* `build`
  * invokes Docker one the client now - the server just consumes images from a registry; consequently, users need a local Docker setup.
  * is a stage command now
  * dumped war argument
* `start` 
  * dumped http(s) arguments
  * replaced appIndex arguments by optional image argument; urls are now named with http and https 
    instead of the app name with an optional SSL.
* `restart` replaced appIndex arguments by optional image argument 
* `stop` dumped apps argument
* `version` now reports client build data and the server version if available
* renamed `app` command to `images`
  * dumped app argument
  * dumped jmxUsage, it's an example in the documentation now
  * remove status fields `jmx-port` and `debug-port`
  * moved all none-image fields to status command:
    `container`, `uptime`, `disk-used`, `environment`, `origin-scm`, `heap`
  * changed `container` to `pod`  
* stage command now print a message if not stages was matched
* TODO: not adjusted yet
  * `ssh` dumped app argument
  * `tunnel` dumped app argument


Server changes
* user tokens expire after 7 days
* start server with kubectl, not docker-compose
* dumped port handling, Kubernetes Proxy does the dispatching; thus, stage urls no longer contain ports
* run server with Java 14
* added `certificate.key` and `certificate.chain` labels (along the existing cert.p12) to give containers access to
  the key and certificate chain
* configuration
  * dumped `vhosts` switch
  * dumped `engineLog` switch
  * renamed `host` to `fqdn`
  * dumped `portFirst` and `portLast`
  * dumped `jmxUsage` template
  * added `kubernetes` api url
* one app per stage
* `start` dumped http(s) arguments
* stage container replaced by
  * deployment
  * pod
  * http+https service
  * fault secrets
  * certificate configMap
* dumped APP_PROPERTIES_FILE and APP_PROPERTIES_PREFIX configuration; the server no longer looks into the war file
  instead, they are currently hard-wired into the client-side configuration; 
* dependency updates: 
  * spring 5.2.3 to to 5.2.9
  * spring security 5.2.1 to 5.3.5
  * spring boot 2.2.2 to 2.3.4
* rest
  * `start` replaced apps argument by optional image arguments; returns a single image now
  * `stop` dumped apps argument, returns an optional String now
  * `appInfo` dumped app argument
  * `ssh` dumped app argument
  * `tunnel` dumped app argument
  * `build` dumped app result field
  * `awaitStartup` now returns a single object representing the urls
  * naming scheme for context directories changed from app:tag to tag (current build is now _)
  * build now creates a repository tag without app name
  * dumped container label `app`


### 5.1.0 (2020-02-04)

dashboard
* performance improvements by using the new  `api/stages` call
* print error reason when an action fails
* added `remove` action
* authentication fix: webjars can be access without auth now
* tweaked share email
* fixed loading stages spinner

client
* adjust to use new `awwait-startup`

server
* removed app name from path for application logs
* status
  * running now returns an optional image
  * dumped `apps`
  * added `images`
* api/stages: added a new `api/stages` call that returns both properties and fields for a list of stages
  with a reduced number for docker calls
  * note that this call returns infos as json, not rendered into strings; in particular,
    the url field is returned as a map
* `awaitStartup` now returns json    
* performance tweaks: reduce number of access log reads
* fixed feedback button (thanks to Julian W)
* fixed NPEs after access logs were removed

dependency updates
* spring framework 5.1.5 -> 5.2.3, security 5.1.5 -> 5.2.1, boot 2.1.4 -> 2.2.2
* gson 2.8.5 -> 2.8.6
* jquery 3.3.1-1 -> 3.4.1
* bootstrap 4.3.1 -> 4.4.1
* slf4j-api 1.7.26 -> 1.7.30
* jnr-unixsocket 0.21 -> 0.25
* junit 4.12 -> 4.13

 
### 5.0.4 (2020-01-14)

server
* fixed corrupted war file in concurrent builds (Lena)

client
* fixed documentation url (thanks to Robin T)


### 5.0.3 (2019-11-29)

client
* create
  * improved error message if stage already exists
  * added `-optional` option to not report an exception if the stage already exists
* added `auth -batch` option to pick credentials from environment variables `STOOL_USERNAME` and `STOOL_PASSWORD`
* setup now creates a `server.yaml` file, not `server.yml`
* support for longer stage names
  * relax stage name length restriction from 30 to 240 characters (because domains in san certificates can be up to 256 characters long).
  * set container hostname to md5(app + stage) + dockerhost to make sure the name does not exceed the 64 character limit of the kernel

server
* config changes
  * renamed DOCKER_HOST to HOST
* added per-stage environment; clients configure it with `stool config environment=FOO:bar` - note the colon to separate key and value;
  explicit environment arguments passed to `start` overwrite per-stage environment values
* "create" now reports a conflict (409) if the stage already exists


### 5.0.2 (2019-08-29)

client
* `setup`: 
  * simplified console output: localhost is now part of the normal environment configuration 
  * you can now re-run setup to update servers
  * fixed problem when "dig" is not installed
  * improved error message when called twice (thanks to Helena R)
  * fix stage indicator for zsh (but with tailing newline ...)
  * simplified arguments used for integration tests
* `build`: origin label now includes svn url follows by svn revision
* `remove`: improved message: "removing backstage" -> "detaching stage"

server
* Docker Api fixes to Adjust to engine 19.03.1: 
  * proper check for warnings
* fixed Docker run to no longer set the current user/group
* fixed permission check in ssh and tunnel api calls
* properly tagged base image (java:1.0.0)

other
* update lazy-foss-parent 1.3.0 to 1.3.1
* update active-markdown 1.0.0 to 1.0.1 (executes ronn in Docker when available)


### 5.0.1 (2019-07-11)

client
* fixed java version detection if JAVA_TOOL_OPTIONS are set (thanks to Radek S)
* create
  * report an error is stage already attached (previous version created the stage *and* reported an error)
  * reject stage names with upper-case characters (thanks to Jan G)
  * added `DEFAULT_EXPIRE` server configuration to define the initial value for the expire property.
* build: 
  * fixed origin to also check parent directories; this is needed if a project is just a module in the git or svn checkout
    (thanks to Jan G)
  * use origin "unknown" if neither git nor svn checkouts are detected
* properly report Stool server name if it returns eof
* setup no longer configures a mail host (because mri.server.lan need authentication)
* update application plugin 1.6.2 to 1.6.3
* added `ssh` client command

server
* added `ssh` api command
* added scheduled task to automatically validate stages every night
* fixed image label name for url context from `url.server` to `url.context`; this fixes the url context not being shown in urls 
  (thanks to Andreas K)
* added `ENGINE_LOG` server configuration
* fixed empty stage validation emails (thanks to Sebastian D)
* fixed container prune before startup: the previous version only purged containers if the images didn't change
  (also fixes images not being removed by new builds)
* start api command: fixed rare https argument problem
* reject `REGISTRY_PREFIX` server configuration with upper cases characters
* speedup dashboard by optimizing docker communication
  * in memory pool
  * save some inspect calls by using the labels returned by list


### 5.0.0 (2019-06-24)

#### Architecture

Stool 5 separates building from running stages: you build on your local workstation, but run on a server. 
To implement this, Stool was split into a client and a server part: the client runs on your local machine, and the server
runs as a daemon on the server. Both communicate via rest. (Note: if you want, you can run them both on the same machine).
Example: to create a new stage for a project on your local workstation, run:

    cd your/project                       # enter the project directory on your local workstation
    mvn clean install                     # or whatever you need to build your project from sources
    stool create teststage@someserver     # create a new stage on the server
    stool build                           # takes the war from your workstation to build a so-called image on the server
    stool start                           # start your stage on the server
    

#### Checkout and build changes
 
Stool is no longer responsible to checkout projects or build wars. Instead, it now expects existing projects with readily built wars. 

As a consequence, all features to manage projects were removed from Stool:
* dumped the distinction between source and artifact stages; Stool now simply looks for `**/target/*.war` files 
* the former `build` command to build a war for a source stage has been replaced by a new `build` command to build an image
* the former `start` command no longer builds an image - it now expects a readily built image
* the `create` command now expects an existing project - scm checkouts have been removed from Stool
* the `remove` command now just removes the stage - the project is *not* removed
* dumped `build` command and the respective configuration
  * `build`, `pom`, `maven.home`, `maven.opts`, `prepare`
  * dumped stage directory lock - Stool now assumes existing war files, so there's no need for locking
  * dumped Maven Embedded dependency
* dumped the `refresh` command because Stool can no longer rebuild a project
  * dumped `refresh` stage config
  * dumped `autoRefresh` stage config
* dumped `committed` configuration - start no longer checks for local modifications
* dumped the `move` command
* dumped the `rename` command; if you need to rename a stage, you now have to remove and re-create it now
* dumped svn credentials handling
* dumped macros
* Stool no longer adjusts the current working directory
  * dumped `cd` 
  * dumped `select` (use `pommes goto` instead)
  * dumped setenv dependency
  

#### Improved Docker integration

* Stool now creates one image per war file (the previous version created one image per Stage)
* replaced Stool config template arguments by Docker build arguments and Docker environment arguments:
  * replaced `mode` by environment argument `mode`
  * replaced `suspend` and `debug` ny environment arguments `suspend` and `debug`
  * replaced `memory` by build argument `memory`
  * replaced `url` by build arguments `server` and `suffixes`
  * replaced `opts` by build argument `opts`
  * replaced `version` by build argument `tomcat`
  * replaced `cookies` by build argument `cookies`
  * added build argument `java`
  * dumped tomcat.certificate (this is part of the server configuration now)
* downloading Tomcat was moved into the standard Dockerfile; removed Tomcat download functionality from Stool
* removed Freemarker templates - a Stool template is a plain Dockerfile now
* images now declare exposed ports via labels; when starting a stage, Stool sets up the respective mapping
* dumped `port` command; instead, `start` has `-http` and `-https` options now
* switched keystore format from JKS to PKCS12
* changes in the standard Dockerfile
  * renamed to `war`
  * no longer set `-Xmx` (let the jvm figure out this)
  * changed server.xml: set `deployXML` to false 
    (since we now have exactly one web application per Tomcat, crossContext has no meaning; 
    and I checked controlpanel trunk - is doesn't contain any symlink, so we can live without `allowLinking`)
* dumped all bind mounts except for the log file directory and the https certificates;
  (instead of the vhosts bind mount the war file is copied into the image now)


#### Other changes

* dashboard
  * is now part of the server - it's the name over the overview page
  * dumped auto-reload of the ui
  * build and refresh are gone - use restart instead
  * added link to logs
  * updated bootstrap to 4.3.1
  
* internal changes
  * dumped Debian packages: use application files from Maven Central instead; they work for Mac OS as well

* stool home layout changes
  * `system` directory is gone - there are no system stages any more
  * `run` directory is gone - ports and locks are implemented by asking docker and storing them in memory
  * `downloads` is gone - Stool no longer performs downloads, place them in your (base) Docker file instead
  
* replaced the `.backstage` directory tree by a single `.backstage` file (containing the attached stage); 
  what happened to former files in this directory:
  * config.json was moved to the server;
  * Docker context directory and `image.log` were moved to the server (use the history command to see the image.log)
  * `container.id` is gone (instead, Stool queries the docker daemon to get running containers)  

* dumped stage id, always use the name now

* removed client-side locking, logging and exception emails
* removed `working` state
* added `running` field that lists the actually running apps
* removed `sleep` state and the corresponding `stop -sleep` flag, it was never used
* dumped isSelected field
* dumped `system` field for stages
* dumped `type` field for stages
* added `remove -stop` option
* added `attach` and `detatch` commands to manage project - stage association
* removed stageName column from server logs
* stool config changes
  * dumped `diskMin`
  * dumped `macros`
  * dumped `search`

* Debug and Jmx ports of stages now bind to localhost; use the new `tunnel` command to gain remote access
* use jmxmp
  - https://stackoverflow.com/questions/11412560/where-to-download-jmxmp
  - https://stackoverflow.com/questions/11413178/how-to-enable-jmxmp-in-tomcat

* dumped user defaults -- only Max had used them
* dumped "Manager" tag from server
* `validate` now sends emails per stage
* dumped `cleanup` command: there's no m2 repository to wipe, and log file rotation has to be part of the server configuration
* adjust help column width to terminal size
* removed `shared` switch - local maven repository is now always the user's Maven repository
* quota now checks the container disk size (more precisely: the writable layer); dumped the original disk size field and replaced it by the 
  former container-disk field

* use Docker api 1.38+, that's the version available on Alex' Ubuntu 14.4 setup
* dumped various 'auto' options
* start now supports an image index argument
* added `app` command to list images of an app
* separate Start and Build commands; as a consequence, the restart command no longer has a nocache option; `build` is a project command

* implementation changes
  * split Stage Class into a Project- and a new Stage class: project is everything around the former stage directory (which is 
    typically the checkout); the new Stage roughly represents the backstage directory
  * tomcatOpts no longer support macros
  * source bind mounts (used for fitnesse) are gone
  * remove Project.updatesAvailable
  * updated inline 1.1.1 to 1.2.0
  

### 4.0.3 (2019-02-13)

* dashboard
  * show stage comment has hover on stage name (stage origin is now show as hover on stage status)
  * sort stages by name (but all reserved stages first)
  * feedback as a popup
  * stage update fixes: properly handle renamed and removed stages 
  * removed progress bar - it was dead
  * removed breadcrumbs, they just cluttered the outout
  * implementation changes
    * removed build stats - they were dead
    * use thymeleaf instead of jsp
    * removed info area, it's wasn't properly used
    * updated bootstrap 3.2.0 to 4.2.1 and jquery 1.11.0 to 3.3.1
    * removed "shepherd" - it's unused
    * removed "driftwood" (https://github.com/mattkanwisher/driftwood.js) - it wasen't set up properly
    * removed "requireJS" - it's overkill
    * removed "glyphicon" font
    * updated to font awesome 5.7
  * dependency updates: 
  * slf4j-api 1.7.21 to 1.7.25
  * logback 1.1.7 to 1.2.3 
  * spring 4.1.6 to 5.1.4
  * spring security 4.0.0 to 5.1.3

* core
  * fixed "stage ping" to reliably detect when tomcat startup is complete 
    (I now check tomcat engine status via jconsole; the previous application url ping was unreliable and cause integration test issues)
  * fixed quota check to include container disk size
  * added `container-disk` status field to indicate the size of the rw layer of the container
  * implementation changes:
    * removed "changes" code (it was unused)
    * dependency updates: 
      * sushi 3.2.0 to 3.2.1
      * gson 2.8.2 to 2.8.5
      * freemarker 2.3.26-incubating to 2.3.28
      * jnr-unixsocket 0.18 to 0.21
      * javamail 1.6.1 to 1.6.2

* build fixes for Java 11
  * update lazy-foss-parent 1.1.0 to 1.2.0
  * update war-plugin 3.0.0 to 3.2.2
  
  
### 4.0.2 (2018-12-07)

* fixed empty .backstage/tomcat/logs directories (thanks to Bernd O.)
* fixed download errors for recent tomcats: verify with sha512 instead of sha1
* fixed delete problem (merged from 3.4.11, thanks to Stefan H)
* Tomcat.fitnesse(): invoke with -e0 to suppress versioning (thanks to Gökhan A)
* changed default tomcat version 9.0.8 to 9.0.13
* update jdeb plugin 1.6 to 1.7
* Java 11 fixes
  * launch with --illegal-access=deny when running on Java 9+
  * add activation dependency
  * update lazy-foss-parent 1.0.2 to 1.1.0 for build fixes
  * update sushi 3.1.7 to 3.2.0


### 4.0.1 (2018-08-01)

* fixed umask in fitnesse launcher script, which causes fitnesse containers to fail when started by a different user
  (thanks to Gökhan)
* fixed 'fault resolve' calls to use the stage's local repository, not the user's local repository


### 4.0.0 (2018-07-13)

Stool 4 runs stages as docker containers: if you start a stage, Stool create a Docker image based on a template and start a container for it.
Resulting changes:

* *templates* replace *extensions*
  * a stage has exactly one template assigned; switch to a different template with `stool config template=othertemplate`
  * a template is a directory with a `Dockerfile.fm` and other files. When starting the stage, Stool passes all *.fm
    files through [FreeMarker](http://freemarker.org/docs/ref_directives.html), invokes `docker build` on the resulting server
    directory, and starts a container for this image
  * in addition to FreeMarker functionality, Dockerfiles can:
    * define configuration fields with `#CONFIG <type> <name>`; use `stool config <template>.<name>=<value>` to change values
    * define status fields with `#STATUS`
  * renamed `$HOME/extensions` directory to `$HOME/templates`, which contains one directory for every available template
  * moved `pustefix` extension functionality into the `tomcat` template; thus `pustefix.mode` is now called `tomcat.mode` 
  * dumped `logstash` extension, it was never used, and we'll have filebeat instead
  * dumped `fault` extension, the tomcat template auto-detects applications that need fault
* `stool status` changes
  * `container` (with the container hash) replaces `tomcat` and `service` (with pids)
  * replaced `jmx` and `jmxHeap` by template-defined status `tomcat.ports` (which also includes the debug port) and `tomcat.heap`
  * replaced `debugger` and `suspend` by template-defined configuration `tomcat.debug` and `tomcat.suspend`
  * renamed `creator` field to `created-by` and `created` to `created-at`
  * dumped `fitnesse`; fitnesse stages are based on the new `fitnesse` template now
  * dumped `other` status field
* stage property changes
  * dumped `tomcat.env` - adjust the template instead
  * dumped `tomcat.service` - the service wrapper is gone
  * `tomcat.version` is a template configuration now; changed default value from 8.5.16 to 9.0.8
  * `tomcat.opts` is a template configuration now
  * renamed `tomcat.heap` to `memory`, changed default from 350 to 400 or 200m for every webapp;
    tomcat heap defaults set to 75% of that
  * replaced `cookies` by tomcat template code, valid values are `OFF`, `STRICT` or `LEGACY`
  * renamed `tomcat.select` to `select`
  * dumped `java.home` because Containers bring their own settings; builds use the globally installed Jdk on the machine
* Stool property cleanup
  * renamed `baseHeap` to `baseMemory`, changed default form 350 to 400
  * dumped `downloadTomcat`, this value is hard-coded into the Tomcat template now
  * removed deprecated `@owner` and `@maintainer` in contact shortcuts
  * renamed `ldapSso` to `ldapUnit`
  * removed `downloadsCache` property, use symlinks for $STOOL_HOME/downloads instead
  * dumped `downloadServiceWrapper` - the service wrapper is gone
  * dumped `certificates`, the respective code has moved into the template
  * added `systemExtras` for directories available to system applications
* dumped `system-import` command (and also the upgrade code)
* dumped redundant `stage` entries in $STOOL_HOME/ports
* cli changes
  * dumped `-debug` and `-suspend` options from `start` and `restart` (and the corresponding defaults); use the `config`
    command instead to set the respective template field
  * dumped `start -fitnesse`; create stage for Fitnesse tests with `template=fitnesse` instead
  * `start -tail` now tails container logs
  * added `start -nocache` and `restart -nocache` option to force rebuild the docker image
* `.backstage` changes
  * `service` is gone
  * renamed `maintainer` to `modified`
  * dumped 'run' subdirectory
  * dumped `tomcat` directory; logs go into `logs` instead, all other sudirectories are now part of the container
* application facing changes
  * dumped Java Service Wrapper, Tomcat is started directly inside the container; as a consequence, stages no longer allocate
    a service wrapper port and a Tomcat stop port
  * dumped -D variables `-Dstool.cp`, `-Dstool.home` and `-Dstool.idlink`, the respective paths are not available in the container
  * Tomcat template
    * disable Tomcat Http 2 Support, it never worked with the connector we use
    * install all of Tomcat in `/usr/local/tomcat`, no longer distinguish CATALINA_HOME and CATALINA_BASE
    * Stool no longer adds

          org.apache.catalina.core.ContainerBase.[Catalina].level = INFO
          org.apache.catalina.core.ContainerBase.[Catalina].handlers = 1catalina.org.apache.juli.FileHandler

      to Tomcat logging.properties

Some implementation notes:
* I did *not* use a Docker client library (e.g. docker-java) because I wanted to keep things small and I wanted to learn the
  rest api myself
* I use jnr-unixsocket to talk to docker daemons; there are more specialized libraries for that (e.g. junixsocket), but
  from what I found they need native code and/or are quiet old

Other changes:
* Tomcat downloads now check the sha1 hash of the download
* stage status `url` renamed to stage `origin` (to resolve naming clash with stage config `url`)
* Dashboard
  * removed `start -fitnesse` command
  * added `cleanup` command
  * added `expire in 1 week` command
  * fixed console output when running without svn credentials

### 3.4.11 (2018-11-23) 

* fixed race condition in `remove` command


### 3.4.10 (2017-10-05)

* renamed `fault.project` property to `fault.projects`
  Migration note: old `fault.project` settings will be ignored, and an empty value is used instead; changing an arbitrary 
  property replaces the `fault.project` with an empty `fault.projects` entry in `.backstage/config.json`
* fixed `stool cd` for stages started with fault
* dashboard: (contributed by maxbraun)
  * added time-left estimate
  * fixed browser freeze it build output becomes too long
* update to sushi 3.8.10 to work with Jdk 1.8.0_144 (thanks to Marcus T)
  * Stool itself did not build
  * `stool start` failed to created the service-wrapper.sh
  

### 3.4.9 (2017-08-01)

* added `fault` template to start stages with fault workspace
* changed default Tomcat version from 8.5.8 to 8.5.16; switched the default download location from http to https
* changed default Service Wrapper version from 3.5.30 to 3.5.32; adjusted default download location from
  http://wrapper.tanukisoftware.com/download/$v to https://wrapper.tanukisoftware.com/download/$v?mode=download
* added `heap` status field indicating percentage of max heap actually used (for Stefan H)
* added user defaults `svn.user` and `svn.password` to define defaults for `-svnuser` and `-svnpassword` (for Stefan H)
* improved quota error message (for Andreas K)
* fixed `stool create gav:...` for multiple artifacts - refresh directory was created more once (for Radek S)
* fixed Tomcat configuration for non-empty server path - the initial slash was missing, which yields a Tomcat warning
  (thanks to Max B)
* fixed stale stage wiper to *not* remove inaccessible stages;
  the old behaviour was very confusing: if a user created a stage in his home (and maybe started it), the stage was 
  considered stale and automatically wiped (without no way to stop it) if a different user invoked Stool; 
  the new behavior issues an 'inaccessible' warning instead of removing it
* former stage fixes (a former stage is not a stage, it's just a directory with a .backstage subdirectory which is not
  references from stool/backstages):  
  * fixed stage indicate to *not* flag former stages as stages
  * fixed 'stool list' and 'stool status' if the current directory is a former stage 


### 3.4.8 (2017-01-23)

* renamed status fields:
  * `maintainer` becomes `last-modified-by`
  * `maintained` becomes `last-modified-at`
* changed default value for `notify` from `@maintainer @creator` to `@creator`
* fixed url defaults, they never worked (thanks to Max)
* ldap tweaks (thanks to Stefan H)
  * dashboard sso is now configured with the `sso` property in `$STOOL_HOME/system/dashboard.properties`
  * ldap unit (for ldap lookup and dashboard sso) is now configurable via ldap.sso 
    (ldap.sso will be renamed to ldap.unit in stool 3.5; it's not renamed now to avoid the migration efford)
* dumped `system-start` and `system-stop`, use `start -all`  and `stop -all` instead
* adjust autoconf url for cp: replaced /xml/config by (/internal-login)
* added UpgradeProtocol configuration for Tomcat 8.5 and 9


### 3.4.7 (2016-12-27)

* fixed build command locking: lock mode none erroneously created a shared lock, which caused a single build command to block any start command
  lock (many thanks to Christina for detecting this!)
* fixed NPE in `stool status` for stale tomcat.pid file (for Simon)


### 3.4.6 (2016-12-08) 

* added `start -fitnesse` and `restart -fitnesse` options to start the fitnesse wikis instead of the applications; 
  also added a `Start Fitness` action to the dashboard; running fitnesse wikis is indicated by the new status field `fitnesse`. 
* disabled the fitnesse template - setting the `fitnesse` property to true will abort `stool start` with an error
* fixed port garbage collection - unused ports are properly freed now
* `stool validate`: fixed duplicate lines in console output
* changed default `tomcat.version` from 8.5.6 to 8.5.8 (which fixes "Unable to add the resource at *somePath* to the cache" )
* changed default `service.version` from 3.5.29 to 3.5.30
* locking tweaks (for Stefan M. and Tesa): 
  * `cleanup` no longer acquires directory locks
  * `list` and `status` no acquires fetches directory locks
  * `move` acquires exclusive directory locks now
  * `remove` acquires exclusive backstage and directory locks now
* $STOOL_HOME is configurable now, default is ~/.stool
* Debian packages reworked:
  * Main package:
    * No longer contains a home directory. Instead, the default is that every user creates his own home.
    * Removed the Debian service. Because there's no longer a unique home to start stages from.
  * Dashboard package: dumped. Because there's no longer a unique home to install it to.
* Development:
  * enable service wrapper debug output if stool was invoked with `-v`
  * simplified build: no longer depends on a custom version of jdeb
  * speedup dashboard build
* improved error message if a backstage directory has no link in `backstages` (for Max).


### 3.4.5 (2016-11-21)

* CAUTION: stop all stages before updating from 3.4.x. (Because the stop mechanism has changed, 3.4.5 cannot stop stages started from older versions.)

* `stool status`: 
  * added new fields:
    * `created` indicates when a stage was created
    * `maintainer` indicates the person that last changed a stage
    * `maintained` is the timestamp of this change
  * fixed exception if service wrapper has no child process

* improved `history` command:
  * speed-up
  * print latest command first
  * simplified details handling: 
    * it's just a global `-details` switch now
    * added `history.details` defaults settings
  * properly log stacktraces, also fixed escaping
  * include create and import commands for the stage
  
* Stool user (for Philipp)
  * Stool now distinguishes the Stool user (used for logging and emails) from the OS user 
    (owner of files and processes, someone with an account on the local machine)
  * configured via environment variable `STOOL_USER`. It not defined, Stool uses the Java system property `user.name`

* chown-less operation:
  * dumped `chown` command since it's no longer needed; instead, admins now have to take care that sharing the files does not cause permission problems
  * removed -autoChown and -autoRechown options
  * removed `owner` status field; changed the dashboard to show the `maintainer` instead

* dumped all sudo rules and `$STOOL_HOME/bin` scripts
  * =chowntree.sd= (no longer needed, because chown is gone; see chown-less operation above). This fixes a critical security problem (thanks to Bernd!)
  * get service-wrapper without sudo
    * instead, it's now started as the current OS user
    * and it's stopped with via anchor file; every user with wx permissions on $backstage/run and $backstage/run/tomcat.anchor can stop a stage 
  * dashboard now invokes Stool commands without sudo, it sets the Stool user to the authenticated user (or, it not authenticated, the Stool user of the dashboard itself)

* stage indicator color is gone: red no longer makes sense, because there's no chown you have to use; and blue was already impossible since  
  the backstage directory moved into the stage directory in Stool 3.4.0

* improved `import` command: re-use backstage directory if it already exists

* improved build command (for Christina)
  * added command parameters to be executed instead of the configured `build` property 
  * added a `-here` option to execute in the current directory

* improved expire property (for Stefan)
  * you can now specify a number instead of a date; it will be translated into the specified number of days from today
  * you can specify the number in your defaults
  * build-in default is now always `never`, it no longer depends on the shared mode; use defaults to configure direfferent values

* fixed `noSuchDirectory` in catalina base, there's now an empty `webapps` directory instead (for Felix).

* bumped lock timeout from 10 to 30 seconds (for Falk).

* Debian packages
  * no longer create a group or a user - both are assumed to already exist now.
  * no longer create a cron jobs - that's up to the user now (because it easy to setup and every body has slightly different needs)


### 3.4.4 (2016-10-17)

* updated default Tomcat version from 8.5.3 to 8.5.6
* fixed bash completion for systems where `ls` is an alias
* removed support for IT-CA certificates
* logstash improvements: `logstash.output` is unused now, the launcher script (configured in `logstash.link` 
  is now expected to generate the configuration as well


### 3.4.3 (2016-09-22)

* `stool validate` now sends a notification if the certificate expires in less than 10 days.
* logstash improvements
  * one logstash process per stage
  * logstash.link now configures a bash script to actually launch the logstash process 
  * logstash.output now specifies a list of files to append for the *input* config generated by stool
* fixed `stool create` gav in shared mode (thanks to Maximilian)
* fixed `stool refresh` on artifact stages
* fixed performance problem in `stool list` (thanks to Gelli)
* fixed Dashboard candel butten - by removing it (thanks to Cosmin)


### 3.4.2 (2016-08-11)

* Dumped predefined STOOL_OPTS from /etc/profile.de/stool
* Global `certificates` property now also accepts a script file name to generate the Java keystore used by Tomcat.
* Added setup `-batch` option
* Converted documentation from docbook to markdown; merged site module into main.


### 3.4.1 (2016-08-02)

* auto options fix: restart running stages if it needs chowning and both -autorestart and -autorechown are specified
* changed default quota from 40000 to 10000
* fixed NPE in `history` command
* fixed quota computation: skip negative values
* `list` now prints the reserved quota (instead of the available space on the home partition)
* fixed `create` and the `diskMin` property to check available space on the target partion, not the partition for Stool home.
* fixed duplicate line in email notifications
* changed logging to create log files with the date appended; gzip them after a day, remove them after 90 days.
* added `creator` status field and `@creator` email alias
* stage indicator tweaks
  * prefix functions with __ to keep namespace and bash completion clean
  * fixed control sequence escaping in stage indicator
  * fixed color to always check the stage directory itself
* implementation changes:
  * speedup `stool list`
  * update Java Mail 1.5.0b1 to 1.5.5

  
### 3.4.0 (2016-07-28)

* auto select:
  * the selected stage is now determined by the current directory
  * to build a stage with the proper environment, you have to use `stool build` now. 
    Stool no longer adjust the environment for the current stage.

* certificate handling
  * generate self-signed certificate if no `certificates` url is specified
  * generate puki certificates for other urls
  
* added support for git urls. To distinguish between svn- and git urls, source stage urls are now prefixed with `svn:` or `git:`.

* system stages are now placed in $HOME/system

* per-user defauls: Stool now checks for a user's `~/.stool.defaults` file. If it exists, Stool loads it as a properties 
  file and uses it as default values for options. For example, a property `refresh.build=true` causes `stool refresh` to build a stage
  without explicitly specify `-build`. To override this default, use `stool refresh -build=false`. 
  For a list of available properties, see the "User defaults" section of the documentation.

* Added `select -fuzzy` option a stage if the specifed name is not found but there's ony one suggestion.

* Logstash template

* `status` command
  * added `cpu`, `mem`, `selected` and `buildtime` fields
  * can also access properties now (actually, name is a property)
  * fixed status field `tomcat` to contain the tomcat pid, not the service wrapper pid; added a new `service` field to contain the 
    service wrapper pid.

* `list` command has the same configurable fields and properties as the `status` command now. Defaults no longer contain the 
  selected stage; the stage directory has been added to the defaults.

* `start` command now deletes $TOMCAT/temp and creates a new empty directory

* The stage name is a property now.
  * `stool rename foo` is gone, use `stool config name=foo` instead. 
  * `stool create -name foo url` is gone, use `stool create url name=foo` instead.

* Global configuration:
  * renamed `contactAdmin` to `admin`.
  * removed `prompt`, it's no longer needed because is a plain executable now. 
  * Pommes is no longer hard-wired, there's a global `search` property to configure arbitrary search tools.

* Stage configuration:
  * default base heap is 350 now
  * added `quota` to limit the disk spaced occupied by the stage.
  * added `notify` to configure email notifications.
  * dumped `sslUrl` 
  * renamed `suffixes` to `url`. Changed the syntax so you can specify protocols, prefixes, suffixes and a server
  * renamed `until` to `expire` (and the value `reserved` to `never`)
  * removed `tomcat.perm` because it's ignored by Java 8.
  * changed default tomcat version from 8.0.26 to 8.5.3
  * changed default service wrapper version from 3.5.26 to 3.5.29
  * changed default diskMin to 1000


* simplified directory structure:
  * backstage directories moved from `$LIB/backstages/stagename` to `stagedir/.backstage`.
  * dumped `$BACKSTAGE/shared`, $BACKSTAGE now directly contains all subdirectories
  * renamed `$BACKSTAGE/conf` to `$BACKSTAGE/service`

* changed system-start and system-stop to always use fail mode after
* start and stop for multiple stages is executed in parallel now. 

* properties for running stages
  * replaced `stool.bin` by `stool.cp`, it points to Stool's application file
  * renamed `stool.backstage` by `stool.idlink`, it points to the link in $LIB/backstages

* improved setup
  * `stool` is now a normal executable, not a shell function. Shell code went to $LIB/shell.rc, and it's only needed for interactive shells. 
  * arbitrary location for `stool` binary
  * $LIB is either `~/.stool` or `/usr/share/stool`
  * improved default configuration
  * atomic upgrade: creates a backup of the lib directory before upgrade; this is restored if the upgrade fails

* fixes
  * Fixed machine reboot problems with stale pid files by making the service wrapper timeout shorter than the systemctl timeout.
  * Fixed broken locks file for command lines containing \n
  * Fixed stage stop for applications with fitnesse template if war files have been remove
  * Fixed `system-import` command.
  
* cleanup
  * dashboard debian package with https only
  * dumped `MACHINE` and `STAGE_HOST` environment variables
  * dumped `$LIB/run/users` directory
  * replaced curl- (stop fitnesse) and wget (downloads) execs by sushi http.
  * fixed `tomcat.service` to actually work for versions other than 3.5.26.

* Implementation changes
  * simplified multi-module build; dumped the `$STOOL_SOURCE` variable.
  * dependency updates:
    * Sushi 2.8.18 to 3.1.1 and inline 1.1.0
    * Maven Embedded 3.11.1 to 3.12.1
    * gson 2.2.4 to 2.7
  * dumped dependencies to slf4j-api and logback

* fixed logging of credential arguments


### 3.3.5 (2016-03-16)

* Fixed dns validation to use proper hostname and port.


### 3.3.4 (2016-03-15)

* Merged system-validate into the validate command.
* Improved validate: send an email if your stage was removed; send on email per user, even for multiple broken stages; a single
  `-repair` option replaces the previous `-stop` and `-repair-locks` options; check that the configured hostname is this machine.
* Fixed stop problem when a stage is in debug mode: set timeout to 1h (instead of infinite).
* Changed initial java.home to optionally remove the tailing /jre. This fixes a problem on Mac OS when invoking Java command line tools like jar.
* Print an error message the user tries to start pustfix editor (to prepare to dump it)
* Print uptime in days if it's more than 48 hours.


### 3.3.3 (2016-03-09)

* Added an uptime field to stool status.
* Fixed synchronization issue in LockManager shutdown hook.
* Validate now detects stale locks and optionally repairs them.
* Allow unauthenticated http://dashboard/stages requests.
* Add java8-sdk as an alternative package dependency (to fix install problem with OpenJdk from PPA).
* Service wrapper: don't restart on OutOfMemory, the user should have a look instead.
* Improved stale backstage wiper: get stop with proper user (and improved error message); wipe backstages without anchor file.


### 3.3.2 (2016-01-18)

* Fixed "devel" version number in exception mails from dashboard.
* stool create: Fixed "Artifact x:y:z not found" error message to include the underlying Maven problem (in particular "Unauthorized").
* Fixed missing date in history command.
* Fixed lost exception when probing svn checkouts.


### 3.3.1 (2015-12-21)

* Fixed missing stool function in non-login shells.
* Fixed missing vhosts in global config.json.
* Fixed run/users/root with empty prompt - 'service stool &lt;cmd>' no longer creates this file.
* Fixed group permissions for backstage/shared/ssl/tomcat.p12.
* Fixed NPE for predicate with unknown property or field; issue proper error message instead.
* Fixed exception emails.
* 'stool start' now reports an error if the hostname name cannot be resolved. And pinging the application has a 5 minutes timeout now.
* Changed the port command to separate application and port by '=' (indead of ':').
* Fixed Stool 3.2.x upgrade code to properly migrate ports of workspaces, special ports, and suffixes in global defaults.
* Generalized suffixes: if a suffix contains [], the resulting application url is the suffix with [] substituted by hostname:port.
* Fixed duplicate -autorechown/-autorestart options in dashboard refresh.
* Moved stage property description from the config command to the documentation (man stool-config).


### 3.3.0 (2015-11-30)

* Renamed overview to dashboard.
* Added '-autostop', '-autorestart', '-autochown' and '-autorechown' options for all stage commands.
* 'stool refresh': Removed implicit re-chowning and re-starting. If you need this functionality, get 'refresh' with the new auto
  options for stage commands: use 'stool refresh -autorechown -autorestart' to get the old behavior.
* 'stool remove': Removed implicit stopping. If you need this functionality, get 'stool remove -autostop'.
* Adjusted the 'chown' command for the new '-autorestart' options: 'chown' now reports an error, if the stage is up.
  Use  '-autorestart' to get the old behavior. The old '-stop' option is gone.
* Removed the 'build -restart' option, use '-autorestart' instead.
* Updated default tomcat version from 7.0.57 to 8.0.26.
* Fixed permission problem on Fitnesse log files; all output is written to backstage/shared/tomcat/logs/fitnesse.log now.
* Retry ldap query if an CommunicationException occured.
* Fitnesse template: Fixed NPE during stage stop when fitnesse is enabled while tomcat is running.
* Fixed tomcat.perm configuration.
* @proxyOpts@ is a normal macro now, and it is no longer computed from the stage owner's environment. If you need proxy configuration,
  you defined it in the global configuration and adjust your stage defaults to use it for all stages.
* Removed Stool update-checks, the packaging system is responsible for this now.
* 'stool config' now supports a "{}" place holder to refer to the previous value. You can use this e.g. to append to a property:
  'stool config "tomcat.opts={} -Dfoo=bar".
* Fixed exception if history argument is not a number. Issue proper error message now.
* You can create artifact stages from either gav: or file: urls now. Example: 'stool create file:///path/to/my.war'.
  You can specify multiple comma-separates urls (instead of the previous multiple coordinates within one gav url).
* Improved setup: Debian package is configurable with debconf now, and it's separated into a stool and a stool-dashboard package now.
* Changed the 'suffix' stage property to 'suffixes', it holds a list now.
* Improved locking (i.e. less waiting for lock messages): replace the old global- and stage-semaphores by one global read/write lock,
  and per-stage a backstage read/write lock and a directory read/write lock.
* 'stool restart': remove 5 seconds sleep.
* Simplified cwd handling: only the following commands change the current directory: create, remove, select and cd.
* Removed allowLinking in server.xml. Because it's no longer needed for Controlpanel and the configuration differs between
  Tomcat 7 and Tomcat 8.
* Fixed wrong capitalization in server.xml for element host alias
* Merged all {stage}/ports files into one {home}/run/ports file.
* Support stages with empty MAVEN_HOME: Stool includes ${home}/maven-settings.xml now, they are used to resolve dependencies if
  the current user has no MAVEN_HOME.
* The separator for list property values is "," now, space is no longer supported.
* 'stool config': Display properties as strings, not as json objects.
* The 'tomcat.env' property is a map now, it defines the environment for applications.
  (Because the old white-listing is hard to debug and problematic when starting an application as a different user.)
* Removed the global 'errorTool' property, report problems to configured 'adminEmail' instead.
* 'stool select': Fixed illegal argument exception if the use specified an absolute path instead of stage name.
* Removed global 'portOverview' property.


### 3.2.0 (2015-08-09)

* Fix persistent ldap connection problem in overview.
* Renamed user "servlet" to "stool".
* Changed permission for stool files from 666 to 664.
* Properly set setgid bit for home directory and every stage directory.
*  Moved $STOOL_HOME/conf/overview.properties to $STOOL_HOME/overview.properties.
* Renamed $STOOL_HOME/wrappers to $STOOL_HOME/backstages.
* Renamed $STOOL_HOME/conf to $STOOL_HOME/run.
* Manual pages for all commands. puc help no longer contains command details, use man stool-&lt;command> instead.
* import: Fixed error message when argument is not a directory.
* Overview: Moved log output into tomcat/logs/overview.log.
* History command: support multiple details argument with ranges.
* History command: properly handle overlapping commands. To implement this i had to fix the log file format: id is unique
  accross file now, because a command started before midnight may finish the next day.
* stool -exception throws an intentional exception now. To test error tool.
