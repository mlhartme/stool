## Changelog 

### 3.4.1 (pending)

* Implementation changes
  * update Java 1.5.0b1 to 1.5.5

  
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
  The following properties are available:
  * `verbose`
  * `exception`
  * `auto.restart`
  * `auto.stop`
  * `auto.rechown`
  * `auto.chown`
  * `import.name`
  * `import.max`
  * `history.max`
  * `refresh.build`
  * `tomcat.debug`
  * `tomcat.suspend`
  * `list.defaults`
  * `status.defaults`
  * `select.fuzzy`

* Added `select -fuzzy` option a stage if the specifed name is not found but there's ony one suggestion.

* Logstash extension

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
  * renamed `suffixes` to `url`. Changed the syntax so you can specify protocols, prefixes, suffixes and a context
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
  * Fixed stage stop for applications with fitnesse extension if war files have been remove
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
* Improved stale backstage wiper: invoke stop with proper user (and improved error message); wipe backstages without anchor file.


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
* 'stool refresh': Removed implicit re-chowning and re-starting. If you need this functionality, invoke 'refresh' with the new auto
  options for stage commands: use 'stool refresh -autorechown -autorestart' to get the old behavior.
* 'stool remove': Removed implicit stopping. If you need this functionality, invoke 'stool remove -autostop'.
* Adjusted the 'chown' command for the new '-autorestart' options: 'chown' now reports an error, if the stage is up.
  Use  '-autorestart' to get the old behavior. The old '-stop' option is gone.
* Removed the 'build -restart' option, use '-autorestart' instead.
* Updated default tomcat version from 7.0.57 to 8.0.26.
* Fixed permission problem on Fitnesse log files; all output is written to backstage/shared/tomcat/logs/fitnesse.log now.
* Retry ldap query if an CommunicationException occured.
* Fitnesse extension: Fixed NPE during stage stop when fitnesse is enabled while tomcat is running.
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
* Fixed wrong capitalization in server.xml for element host alias.
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
