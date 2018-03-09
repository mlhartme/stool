# Stool

## Introduction

Stool is a command line tool that provides a lifecycle for stages: create, build, start, stop, remove.
A stage is a Docker Container with web applications, built from sources or downloaded as artifacts.

### Quick Tour

For setup instructions, please read the respective section below. The following assumes that Stool is properly set up.

Here's an example, what you can do with Stool. 

Create a new stage by checking out application sources:

    stool create git:ssh://git@github.com/mlhartme/hellowar.git

Build the application:

    stool build

Start it:

    stool start

To see the running application, point your browser to the url printed by the `start` command.

You can get

    stool status

to see if your application is running and to see the application urls.

To remove the stage, stop the application with

    stool stop

and dump it from your disk with

    stool remove

You can create an arbitrary number of stages. Invoke

    stool list

to see what you have created and not yet removed. To switch to another stage, get

    stool select otherstage

Use 

    stool history
    
to see the Stool commands executed for your stage.

You can get help with

    stool help

to see a list of available commands. You can append a command to get more help on that, e.g.

    stool help create
    
prints help about `create`.

### Conventions

* Stool is written with a capital S)
* `type writer font` marks things to type or technical terms from Stool.
* italics mark *text* to be replaced by the user
* bold face highlights term in definition lists
* synopsis syntax: `[]` for optional, `|` for alternatives, `...` for repeatable, `type writer` for literals, *italics* for replaceables)
* $STAGE denotes the stage directory of the respective stage; 
* $STOOL_HOME denotes Stools home directory 


## Concepts

### Stage

A stage defines a Docker, typically containing a Tomcat servlet container (http://tomcat.apache.org) with one or more
Java web applications (https://en.wikipedia.org/wiki/Java_Servlet). A stage has a

* **directory**
  Where the stage is stored in your file system, it holds the source code or the war files of this stage.
  This is where you usually work with your stage. The directory is determined when you create a
  stage. You can change the stage directory with `stool move`.
* **id**
  Unique identifier for a stage. The id is generated when creating a stage and it is never changed.
  However, users normally work with the stage name instead. You can see it with `stool status id`.
* **name**
  User readable identification for a stage. Usually, the name is unique. The name of the selected stage 
  is shown in your shell prompt, you use it to switch between stages, and it's usually part of the application
  url(s). The name is determined when you create a stage (in most cases it's simply the name of the stage
  directory). You can change the name with `stool config name=`*newname*.
* **url**
  Specifies where the web applications come from: A Subversion URL, a git url, Maven coordinates, or
  a file url pointing to a war file. Examples:
  
      git:ssh://git@github.com/mlhartme/hellowar.git
      svn:https://github.com/mlhartme/hellowar/trunk
      gav:net.oneandone:hellowar:1.0.2
      file:///home/mhm/foo.war

* **type**
  How the stage contains the application(s): source - checkout of project sources, or artifact - an artifact
  from a Maven repository. The stage url implies the stage type.
* **state**
  one of
  * **down**
    stage is not running, applications cannot be accessed. This is the initial state after creation or after
    it was stopped.
  * **up**
    stage is running, applications can be accessed via application url(s). This is the state after successful
    start or restart.
  * **sleeping**
    stage is temporarily not running; state after stage was stopped with `-sleep`.
    This state is used e.g. when a machine is rebooted, it flags the stages that should be started once the
    machine is up again.
  You can check the state with `stool status` or `stool list`.
* **last-modified-by**
  The user that last changed this stage.

### Selected stage and stage indicator

The selected stage is the stage the current working directory belongs to. In other words: your current working directory 
is the stage directory or a (direct or indirect) subdirectory of it. Unless otherwise specified, stage commands operate 
on the selected stage.

The stage indicator `{somestage}` is display in front of your shell prompt, it shows the name of the selected stage.

If you create a new stage, Stool changes the current working directory to the newly created stage directory. Thus, the new stage
becomes the selected stage. `stool select` changes the current working directory to the respective stage directory,
thus it is just a convenient way for cd'ing between stage directories.

The stage indicator is invisible if you have no stage selected; select a stage to see a stage indicator.

### Properties

Stool is configured via properties. A property is a key/value pair. Value has a type (string, number, date,
boolean, list (of strings), or map (string to string)). Stool distinguishes Stool properties and stage
properties. Stool properties are global settings that apply to all stages, they are usually adjusted by system
administrators (see [stool properties](#stool-properties)). Stage properties configure the
respective stage only, every stage has its own set of stage properties. You can adjust stage properties 
with [stool config](#stool-config).

In contrast, every stage has status fields, you can view them with `stool status`. Status fields are similar to
properties, but they are read-only.


### Backstage

Every stage directory contains a backstage directory `.backstage` that stores Stool-related
data about, e.g. the stage properties or log files of the applications. The backstage directory is created
when you create or import the stage. `$STOOL_HOME/backstages` contains a symlink *id*->*backstage* for every stage.
Stool uses this to iterate all stages.

Stool removes backstage symlinks either explicitly when you run `stool remove`, or implicitly when it detects that
the target directory has been removed. Stool checks for - and cleans - stale backstage links before every command.


### Stage Expiring

Every stage has an `expire` property that specifies how long the stage is needed. You can
see the expire date with `stool config expire`. If this date has passed, the stage is called
expired, and it is automatically stopped, a notification email is sent and you cannot start it again
unless you specify a new date with `stool config expire=`*yyyy-mm-dd*.

Depending on the `autoRemove` Stool property, an expired stage will automatically be removed after
the configured number of days. Stage expiring helps to detect and remove unused stages, which is crucial for
shared machines. If you receive an email notification that your stage has expired, please check if your stage
is still needed. If so, adjust the expire date. Otherwise, remove the stage.

### User defaults

Users can define default values for various command line option by placing a properties file `.stool.defaults` in
their home directory. If this file exists, Stool uses the contained properties as default values for various options.
For example, a property `refresh.build=true` causes `stool refresh` to build a stage without
explicitly specifying the `-build` option. (Note: To override this default, use `stool refresh -build=false`).

Supported user default properties:

* **verbose**
  controls the `-v` option for every Stool command
* **exception**
  controls the `-e` option for every Stool command
* **auto.restart**
  controls the `-autorestart` option for every stage command
* **auto.stop**
  controls the `-autostop` option for every stage command
* **import.name**
  controls the `-name` option for the import command
* **import.max**
  controls the `-max` option for the import command
* **history.max**
  controls the `-max` option for the history command
* **history.details**
  controls the `-details` option for the history command
* **list.defaults**
  controls the `-defaults` option for the list command
* **status.defaults**
  controls the `-defaults` option for the status command
* **select.fuzzy**
  controls the `-fuzzy` option for the select command
* **refresh.build**
  controls the `-build` option for the refresh command
* **svn.user** and **svn.password** 
  credentials the `-svnuser` and `-svnpassword` options for every Stool command.


### Dashboard

The dashboard is a special stage you can install to control stages via browser.

## Commands

### stool

Stage tool

#### SYNOPSIS

`stool` *global-option*... [*command* *argument*...]

#### DESCRIPTION

Stool is a command line tool that provides a lifecycle for stages: create, configure, build, start,
stop and remove. A stage contains web applications built from source or available as artifacts.
*command* defaults to `help`.

#### Commands

[//]: # (ALL_SYNOPSIS)

`stool` *global-option*... [*command* *argument*...]


`stool` *global-option*... `help`


`stool` *global-option*... `version`


`stool` *global-option*... `create` [`-quiet`] (*url* | *directory*) *key*`=`*value*...



`stool` *global-option*... `import` [`-max`] [`-name` *template*] *directory* ...



`stool` *global-option*... `select` *stage*|`none`



`stool` *global-option*... *stage-command* [`-all`|`-stage` *predicate*] [`-fail` *mode*] [`-autostop`|`-autorestart`] *command-options*...


`stool` *global-option*... `build` *stage-option*... [`-here`] *command*


`stool` *global-option*... `remove` *stage-option*... [`-force`] [`-batch`] [`-backstage`]


`stool` *global-option*... `start` *stage-option*... [`-fitnesse`|`-debug`|`-suspend`] [`-tail`]


`stool` *global-option*... `stop` *stage-option*... [`-sleep`]


`stool` *global-option*... `restart` *stage-option*... [`-fitnesse`|`-debug`|`-suspend`]



`stool` *global-option*... `refresh` *stage-option*... [`-build`] [`-restore`]



`stool` *global-option*... `history` *stage-option*... [`-details`] [`-max` *n*] 


`stool` *global-option*... `cd` *stage-option*... *target*



`stool` *global-option*... `config` *stage-option*... (*key* | *value*)...


`stool` *global-option*... `move` *stage-option*... *dest*


`stool` *global-option*... `port` *stage-option*... *application*`=`*port*...


`stool` *global-option*... `status *stage-option*... (*field*|*property*)...



`stool` *global-option*... `list` *stage-option*... (*field*|*property*)...


`stool` *global-option*... `cleanup` *stage-option*...


`stool` *global-option*... `validate` *stage-option*... [`-email`] [`-repair`]

[//]: # (-)

#### Global options

* **-v** enables verbose output
* **-e** prints stacktrace for all errors
* **-svnuser** specifies the user name for Subversion operations
* **-svnpassword** specifies the password for Subversion operations

Note: Subversion operations (e.g. checkout a new stage) get the `svn` command with credentials specified by `-svnuser` and `-svnpassword`. 
If not specified, the defaults of svn `svn` command are used (usually stored in `~/.subversion/auth`).


#### Stool Properties

Stool's global configuration is stored in `$STOOL_HOME/config.json`. It defines following [properties](#properties).

* **admin** 
  Email of the person to receive validation failures and exception mails. Empty to disable these emails.
  Type string. Example: `Max Mustermann <max@mustermann.org>`.
* **autoRemove**
  Days to wait before removing an expired stage. -1 to disable this feature. Type number. 
* **baseMemory**
  Defines how to compute the initial `memory` property for new stages: `baseMemory` mb for every application. Type number.
* **certificates**
  Empty or script or url to generate certificates to make stages available via https. Empty to generate self-signed
  certificates. Otherwise, if it starts with `http://` or `https://` Stool generates a `csr` and posts it to
  *certificates*, expecting back the certificate. Otherwise, Stool invokes the specified script with the desired domain as
  an argument. The script is invoked in the *.backstage/ssh* directory, it is expected to generate a =tomcat.jks= keystore file
  with Java's standard keystore password. (A note about re-generation: when starting a stage, Stool checks if =tomcat.jks= 
  already exists; if not, certificate generation is triggered). Type string.
* **committed**
  `true` if users have to commit source changes before Stool allows them to start the stage. Type boolean.
* **defaults**
  Default values for stage properties. Keys are stage url prefixe, values are maps of property name/values to set for 
  a new stage with a matching url. Type map.
* **diskMin**
  Minimum mb free space. If less space is available on the target partition, Stool refuses to create new stages. Type number. 
* **downloadTomcat**
  Url pattern where to download Tomcat. Available variables: `${version}` and `${major}`. Type string.
* **downloadServiceWrapper**
  Url pattern where to download Java Service Wrapper. Available variables: `${version}` and `${major}`. Type string.
* **downloadCache**
  Directory where to store Tomcat or Java Service Wrapper downloads. Type string.
* **hostname**
  Fully qualified hostname used to refer to this machine in application urls and emails. Type string.
* **ldapCredentials**
  Password for Ldap authentication. Ignored if ldap is disabled. Type string.
* **ldapPrincipal**
  User for Ldap authentication. Ignored if ldap is disabled. Type string.
* **ldapSso**
  Will be renamed to ldapUnit in future version. Specifies the "organizational unit" to search for users. Type string.
* **ldapUrl**
  Ldap url for user information. Empty string to disable ldap. Type string.
* **macros**
  String replacements for stage properties. Stool automatically defines `directory` for the respective stage directory,
  `localRepository` for the local Maven repository of this stage, `svnCredentials` as expected by `svn`, and `stoolSvnCredentials`
  as expected by `stool`. Type map.
* **mailHost**
  Smtp Host name to deliver emails. Empty to disable. Type string.
* **mailUsername**
  Username for mailHost. Type string.
* **mailPassword**
  Password for mailHost. Type string.
* **portFirst**
  First port available for stages. Has to be an even number >1023. Type number.
* **portLast**
  Last port available for stages. Has to be an odd number >1023. Type number.
* **quota**
  Megabytes of disk spaces available for stages. The sum of all stage quota properties cannot exceed this number. 0 disables this
  feature. You'll usually set this to the size of the partition that will store your stages. Note that this quota cannot always
  prevent disk full problem because stages can be placed on arbitrary partitions. Type number. 
* **shared**
  `true` if multiple user may work on stages. When set to true, Stool uses `.backstage/.m2` (instead of the current user's 
  `~/.m2/repository`) as local Maven repository. Type boolean.
* **search**
  Command line to execute if `stool create` is called with an % url. When calling the command, the placeholder `()` is replaced by the url.
  Default is empty which disables this feature.
* **vhosts**
  `true` to create application urls with vhosts for application and stage name.
  `false` to create application urls without vhosts. (Note that urls always contain the port to
  distinguish between stages). Type boolean. If you want to enable vhosts you have to make sure you
  have the respective DNS * entries for your machine.

#### Ports

Stool allocates ports in pairs: an even and the following odd number. To choose a port for a given Stage, Stool computes a hash between
portFirst and portLast. If this pair if already allocated, the next higher pair is checked (with roll-over to portFirst if necessary).
Even ports are used for http, odd ports for https. In addition to one pair for each application of the stage, Stool allocates two pairs 
for internal use for every stage (jmx ports, debugger port, tomcat stop port and service wrapper port). Thus, one stage needs at least 
3 pairs (= 6 ports).

You can see the currently allocated ports in `$STOOL_HOME/run/ports`


#### Environment

`STOOL_OPTS` to configure the underlying JVM.

`http_proxy`, `https_proxy` and `no_proxy` to configure proxy settings.

Stool also accepts the system properties `stool.http.proxyHost`, `stool.http.proxyPort`, `stool.http.nonProxyHost`, and the same with `https`.
And standard system properties for proxy configuration, see https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html.
Precedence is from most specific to most general, i.e. if you use stool properties, neither standard properties nor http environment variables
will be checked. 
 

#### See Also

Homepage: https://github.com/mlhartme/stool

Documentation: https://github.com/mlhartme/stool/blob/master/main/documentation.md

Invoke `stool help` *command* to get help for the specified command.


## System commands

Commands that do not deal with individual stages.

### stool-help 

Display man page

#### SYNOPSIS

`stool` *global-option*... `help`

#### DESCRIPTION

Prints help about the specified *command*. Or, if *command* is not specified, prints help about Stool.

### stool-version 

Display version info

#### SYNOPSIS

`stool` *global-option*... `version`

#### DESCRIPTION

Prints Stool's version info. Use the global `-v` option to get additional diagnostic info.

### stool-create

Create a new stage

#### SYNOPSIS

`stool` *global-option*... `create` [`-quiet`] (*url* | *directory*) *key*`=`*value*...


#### DESCRIPTION

Creates a new stage- and backstage directory and enters the stage directory. In most cases, you can get `stool create` 
similar to `svn checkout`: with an url and a directory to checkout to.

*url* specifies the application you want to run in your stage. In many cases, the url is a subversion url prefixed
with `svn:` or a git url prefixed with `git:`. Stool performs a checkout resp. a clone. Output of the 
command is printed to the console unless the `-quiet` option is specified.

To create an artifact stage, specify a war file, a file url or a GAV url. You may specify multiple comma-separated urls, 
and you may specify `=`*name* if you want to assign a non-default vhost for an application.

Instead of a *url* you can specify `%`*searchstring*. This will search the configured search tools for the specified 
string, show all matching scm urls, and ask you to select one.

*directory* specifies the stage directory to hold your application. If not specified, the current directory
with the last usable segment of the `url` (i.e. the last segment that is not trunk, tags, or branches) is used. 
You can specify an arbitrary directory, as long as it does not exist yet and the parent directory exists.

The new stage is configured with default stage properties. You can specify *key-value* pairs to override the 
defaults, or you can change the configuration later with [stool config](#stool-config).

For artifact stages, the `maven.home` property is used to locate Maven settings which configure the repositories (and 
optional credentials) to download artifact(s) from.

The stage name property defaults to the directory name, i.e. the last segment of the absolute path to the stage directory.

`create` reports an error if the available free disk space is low (the threshold is specified by the `diskMin` 
Stool property.


#### Examples

Create a source stage from svn: `stool create svn:https://github.com/mlhartme/hellowar/trunk`

Create a source stage from git: `stool create git:git@github.com:mlhartme/hellowar.git`

Create an artifact stage: `stool create gav:net.oneandone:hellowar:1.0.3`

Create an artifact stage from a file: `stool create file:///my/path/to/artifact.war`

Create an artifact stage with multiple applications: `stool create gav:net.oneandone:hellowar:1.0.2,gav:net.oneandone:hellowar:1.0.3=second`

### stool-import

Create stages for one or many existing directories

#### SYNOPSIS

`stool` *global-option*... `import` [`-max`] [`-name` *template*] *directory* ...


#### DESCRIPTION

Scans *directory* for stage candidates and offers to import them. The candidates you select will be imported, 
i.e. a backstage directory for the stage directory is created. If the scan only yields a single candidate, 
it will be imported and selected without additional interaction.

Scanning checks the specified directory (and recursively all subdirectory). It it finds a directory which is 
a svn checkout or a git clone and it contains a =pom.xml= file, this directory becomes a stage candidate.

*max* limits the number of candidates to collect.

*template*
is a string defining the stage name. And any occurrence of `%d`
will be replaced by the current directory name. Default template is `%d`.


### stool-select

Jump between stage directories

#### SYNOPSIS

`stool` *global-option*... `select` *stage*|`none`


#### DESCRIPTION

Prints the selected stage when called without argument.

Otherwise cds to the stage directory of the specified *stage*.
When called with `none`: cds to the parent directory of the current stage.

If the specified stage is not found, the command prints an error message and lists stages that
you could have meant. If you also specified the `-fuzzy` option and there's only a
single candidate, this stage will be selected.

## Stage Commands

Most Stool commands are stage commands, i.e. they operate on one or multiple stages. Typical
stage commands are `status`, `build`, `start`, and `stop`. Note that `create` is not a stage command 
because it does not initially have a stage to operate on (although it results in a new (and selected) 
stage).

All stage commands support stage options, get `stool help stage-options` for documentation.

### stool-stage-options

Options available for all stage commands

#### SYNOPSIS

`stool` *global-option*... *stage-command* [`-all`|`-stage` *predicate*] [`-fail` *mode*] [`-autostop`|`-autorestart`] *command-options*...

#### Selection options

By default, stage commands operate on the selected stage (as shown in the stage indicator). You can change this by specifying a selection option.

`-all` operates on all stages

`-stage` *predicate* operates on all matching stages. The syntax for predicates is as follows:

              or = and {',' and}
              and = expr {'+' expr}
              expr = NAME | cmp
              cmp = (FIELD | PROPERTY) ('=' | '!=') (VALUE | prefix | suffix | substring)
              prefix = VALUE '*'
              suffix = '*' VALUE
              substring = '*' VALUE '*'
              NAME       # name of a stage
              FIELD      # name of a status field
              PROPERTY   # name of a configuration property
              VALUE      # arbitrary string


The most basic predicate is a simple `NAME`. It matches only on the specified stage. This is handy
to get one command for a stage without selecting it.

Next, a predicate *FIELD*`=`*VALUE* matches stages who's status field has the specified value.
*PROPERTY*`=`*VALUE* is similar, it matches stage properties.

#### Failure mode

Since stage commands operate on an arbitrary number of stages, you might want to specify what to do if the command
fails on some stages. That's what `-fail` *mode* is for.

Mode `normal` reports problems immediately and aborts execution, Stool does not try to get the command 
on remaining matching stages. This is the default.

`after` reports problems after the command was invoked on all matching stages.

`never` is similar to `after`, but reports warnings instead of errors (and thus, Stool always returns with exit code 0).

#### Auto options

Stage commands provide auto options do deal with stages that are not stopped.

With `-autorestart`, Stool checks the state of a stage. It the stage is up, Stool stops the stage,
invokes the actual command, and starts the stage again. `-autostop` is similar, but the stage is not started
again.

#### Examples

`stool status -stage foo` prints the status of stage `foo`.

`stool config -stage tomcat.version!=7.0.57 tomcat.version` prints all Tomcat versions other than 7.0.57.

`stool start -all -fail after` starts all stages. Without `-all`, the command would abort
after the first stage that cannot be started (e.g. because it's already running).

`stool stop -stage state=up` stops all stages currently up, but aborts immediately if one stage fails to stop.

### stool-build

Build a stage

#### SYNOPSIS

`stool` *global-option*... `build` *stage-option*... [`-here`] *command*

#### Description

Executes the specified command or the command specified by the `build` property. Executes in the current directory
when called with `-here`; otherwise, executes in the stage directory. Sets MAVEN_HOME, MAVEN_OPTS and JAVA_HOME 
as configured for the stage. Reports an error if the stage is not owned or if the stage is up.

You can see the configured build command with `stool config build`, and you can change it with
`stool` *global-option*... `config "build="`*your command*`"`. Quotes are mandatory if your command contains spaces.

The pre-defined build command for artifact stages does nothing. Thus, you can get
`stool build` for artifact stages, it just has no effect.

If you get `build` from the dashboard application, the build command executes in the environment
defined for the dashboard stage with the additional environment variables mentioned above.

The differences between using `stool build` and executing a build command directly is the execute directory
and the environment. When working in shared mode, you usually you have to use `stool build` because shared machine 
need a separate local Maven repository for every stage - and that's configured in the stage environment.


[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)

### stool-remove

Remove a stage

#### SYNOPSIS

`stool` *global-option*... `remove` *stage-option*... [`-force`] [`-batch`] [`-backstage`]

#### Description

Removes the stage, i.e. deletes the stage directory and the backstage directory.
Changes the current directory to the parent of the then deleted stage directory.

Reports an error is the stage is up or if the stage has uncommitted changes. In this case, stop the stage and inspect
the uncommitted changes; either commit them or revert them. Alternatively, you can disable this check with the
`-force` option.

Also, before removing anything, this command asks if you really want to remove the stage. You can suppress this interaction 
with the `-batch` option.

If you specify the `-backstage` option, only the backstage directory will be deleted and the stage is removed from Stool's 
list of stages. This is useful to "unimport" a stage, i.e. revert the effect of `stool import`.

[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-start

Start a stage

#### SYNOPSIS

`stool` *global-option*... `start` *stage-option*... [`-fitnesse`|`-debug`|`-suspend`] [`-tail`]

#### Description

Creates the necessary configuration and starts Tomcat with all applications for this stage. If the stage is an artifact stage, you can 
start it right away; otherwise, you have to build it first. Depending on your application(s), startup may take a while.

Startup is refused if your stage is expired. In this case, use `stool config expire=`*newdate*
to configure a new `expire` date.

Startup is also refused if the disk quota exceeded. In this case, delete some unused files, try `stool cleanup`, or use 
`stool config quota=`*n*.

`-fitnesse` starts the FitNesse Wiki instead of the web application.

`-debug` and `-suspend` enable the debugger. The difference is that `-suspend` waits for the debugger to connect before running 
any application code.

Use the `-tail` option to start tomcat and get `catalina.out` printed to the console. Press ctrl-c to stop watching `catalina.out`, 
the application will continue to run. Alternatively, you can tail the current stage manually with `stool cd logs && tail -f catalina.out`

`start` generates a Tomcat base directory `$STAGE/.backstage/tomcat` if it does not yet exist. If it exists, only the server.xml 
is updated by taking server.xml.template and adding all apps to it. This allows for manual changes in the base directory. `start` 
deletes all files in Tomcat's `temp` directory.

The Tomcat version is configurable with `stool config tomcat.version=`*version*. If you change it, you have to stop the stage, 
delete the `$STAGE/.backstage/tomcat` directory and start the stage. The respective Tomcat will be downloaded automatically 
to the directory specified by the global `downloadCache` property (default is `$STOOL_HOME/downloads`). Alternatively,
you can place customized Tomcats tar balls into this directory, provided they unpack to a directory that matches the base 
file name of the `tar.gz` file.

Technically, Tomcat is started by the Java Service wrapper (http://wrapper.tanukisoftware.com/). You can configure the
version of the wrapper with the `tomcat.service` property. Run this command with `-v` enabled service wrapper debug output.

If you want to re-generated all files created by this command, use `stool cd backstage && rm -rf service ssl tomcat`. 
This is useful e.g. to get certificates regenerated.

The current working directory of the started VM is the stage directory.

The environment of the started application is the environment specified by the `tomcat.env` property. In addition, you get 
the `HOME` and `USER` variables of the current OS user. In addition, Stool defines two Java System properties in the VM running this 
stage:
* `stool.cp` points to the stool executable that started this stages
* `stool.home` point to Stool's home directory


[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-stop

Stop a stage

#### SYNOPSIS

`stool` *global-option*... `stop` *stage-option*... [`-sleep`]

#### DESCRIPTION

Stops Tomcat of the respective stage. If `-sleep` is specified, the stage is also marked as sleeping.

This command signals Tomcat to shutdown all applications and waits for up to 4 minutes to complete this. After this timeout,
Tomcat is killed (with -9). If Tomcat shutdown is slow, try to debug the applications running in this stage and find out how
they handle the shutdown request. Or send `kill -3` to the Tomcat pid and check the stack trace in catalina.out.

Technically, Stool removes the service wrapper anchor file to signal the shutdown request to the service wrapper. The service wrapper
first sends a normal kill signal to Tomcat. If that times out, it sends a kill -9 as described above.  
Using anchor files is unusual, you'd normally send a kill signal to the service wrapper. But that's only allowed for the user that 
actually startet the stage (or for root). By using anchor files, any user with `rwx` permissions on `$STAGE/.backstage/run` can stop a stage.

[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-restart

Restart a stage

#### SYNOPSIS

`stool` *global-option*... `restart` *stage-option*... [`-fitnesse`|`-debug`|`-suspend`]


#### DESCRIPTION

Shorthand for `stool stop && stool start` with the specified options.

[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-refresh

Refresh a stage

#### SYNOPSIS

`stool` *global-option*... `refresh` *stage-option*... [`-build`] [`-restore`]


#### DESCRIPTION

Reports an error if the stage is not owned or if the stage is up.

For artifact stages: checks for new artifacts and installs them if any.

For source stages: invokes the command specified by the `refresh` property. If `-build`
is specified, also runs the command specified by the `build` property.

[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-history

Display command history

#### SYNOPSIS

`stool` *global-option*... `history` *stage-option*... [`-details`] [`-max` *n*] 

#### DESCRIPTION

Prints the Stool command that affected the stage. Specify `-details` to also print command output. Stops after the 
specified max number of commands (*n* defauls is 50).

[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-cd

Jump to a directory

#### SYNOPSIS

`stool` *global-option*... `cd` *stage-option*... *target*


#### DESCRIPTION

Changes the current working directory to the specified *target*:

* **(empty)** the stage directory
* **backstage** the backstage directory
* **(otherwise)** the specified direct or indirect sub-directory of the backstage directory

[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


#### Example

`stool cd logs` will jumps to `tomcat/logs` inside your backstage directory.

### stool-config

Manage stage properties

#### SYNOPSIS

`stool` *global-option*... `config` *stage-option*... (*key* | *value*)...

#### DESCRIPTION

This command gets or sets stage [properties](#properties). Caution: `config` does not deal with Stool properties, see `stool help` for that.

When invoked without arguments, all stage properties are printed.

When invoked with one or more *key*s, the respective properties are printed.

When invoked with one or more assignments, the respective properties are changed.

Property values may contain {} to refer to the previous value. You can use this, e.g., to append to a property:
`stool config "tomcat.opts={} -Dfoo=bar"`.

If you want to set a property to a value with spaces, you have to use quotes around the key-value pair.
Otherwise, the Stool does not see what belongs to your value.

If you change a property, you have to get the necessary re-builds or re-starts to make the changes
effective. E.g. if you change `tomcat.heap`, you have to run `stool restart` to make the change effective.

Properties have a type: boolean, number, date, string, list of strings, or map of strings to strings.

Boolean properties have the values `true` or `false`, case sensitive.

Date properties have the form *yyyy-mm-dd*, so a valid value for `expire` is - e.g. -`2016-12-31`. Alternatively, 
you can specify a number which is translated into the date that number of days from now (e.g. `1` means tomorrow).

List properties (e.g. `select`) are separated by commas, whitespace before and after an item is ignored.

Map properties (e.g. `tomcat.env`) separate entries by commas, whitespace before and after is ignored.
Each entry separates key and value by a colon. Example `PATH:/bin, HOME:/home/me`

[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


#### Available stage properties

Note that the default values below might be overwritten by Stool defaults on your system.

* **autoRefresh**
  True if you want the dashboard to automatically refresh the stage every minute. Type boolean.
* **build**
  Shell command executed if the user invokes `stool build`. Type string.
* **comment**
  Arbitrary comment for this stage. Stool only stores this value, it has no effect. Type string.
* **cookies**
  Enable or disable cookies. Type boolean. Default value: `true`
* **expire**
  Defines when this stage [expires](#stage-expiring). Type date.
* **java.home**
  Install directory of the JDK used to build and run this stage. Type string.
* **maven.home**
  Maven home directory used to build this stage or resolve artifacts. Type string.
* **maven.opts**
  MAVEN_OPTS when building this stage. Type string. Default value: (empty)
* **notify**
  List of email address or `@last-modified-by` or `@created-by` to send notifications about
  this stage. Type list. Default value: `@created-by`.
* **pom**
  Path of the pom file in the stage directory. Type string. Default value: `pom.xml`.
* **prepare**
  Shell command executed after initial checkout of a source stage. Type string.
* **refresh**
  Shell command executed for source stage if the user invokes `stool refresh`.
  Type string. Default value: `svn @svnCredentials@ up`
* **quota**
  Max disk space for this stage in mb. You cannot start stages if this space exceeded.
  The sum of all quotas cannot exceed the Stool quota. Type number.
* **tomcat.env**
  The environment to start Tomcat with. Type map. This is intentionally not the environment of the
  current user because any user must be able to start the stage and get the same behavior.
* **tomcat.opts**
  CATALINA_OPTS without heap settings. Type string. Default value: (empty)
* **tomcat.heap**
  Java heap memory ("-Xmx") in mb when running Tomcat. Type number.
* **select**
  List of selected applications. When starting a stage, Stool configures the container only for the selected
  applications. If none is selected (which is the default), it configures all applications. Type list.
  Default value: (empty)
* **tomcat.version**
  Tomcat version to use. Type string. Default value: `9.0.5`. If you change this property,
  you have to stop tomcat, delete the `.backstage/tomcat` directory, and start Tomcat again.
* **url**
  A pattern that define how to build the application urls: a sequence of strings and alternatives, where
  alternatives are strings in brackets, separated by |. Example: `(http|https)://%a.%s.%h:@p/foo//bar`
  Strings may contain place holders: `%a` for the application name, `%s` for the stage name, `%h` for the hostname,
  and `%p` for the port. A double slash in the path part of the url separates the web application context from a normal path
  suffix. Thus, the above application is started in context `foo`. If the path part contains no double slash, the application
  is started in the root context.

#### Examples

`stool config tomcat.heap` prints the current value for Tomcat heap space.

`stool config tomcat.heap=1000` sets the tomcat heap size to `1000` mb.

`stool config "build=mvn clean package"` sets a value with spaces.

`stool config select=foo,bar` configures a list property. Do not use spaces around
the comma because the shell would consider this as a new key-value argument -- or quote the whole argument.

### stool-move

Move the stage directory

#### SYNOPSIS

`stool` *global-option*... `move` *stage-option*... *dest*

#### DESCRIPTION

Moves the stage directory without touching the stage id or stage name. If *dest* exists, it is moved into it. 
Otherwise it is moved into the parent of dest with the specified name. This is the same behavior as the unix `mv` 
command, but it also adjusts Stool's backstage links.

You might have to re-build your application after moving the stage if you have development tools that store absolute paths
(e.g. Lavender ...).

[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-port

Allocates ports for the current stage

#### SYNOPSIS

`stool` *global-option*... `port` *stage-option*... *application*`=`*port*...

#### DESCRIPTION

Allocates the specified ports for this stage. *application* specifies the application to use this port.
*port* is the http port, *port*+1 is automatically reserved for https. When starting a stage, unused allocated 
ports are freed.

This command is if you have to explicitly assign specific ports to a given stage. You'll normally not use it
and instead get random ports automatically allocated when you start the stage. 


[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-status

Display stage status

#### SYNOPSIS

`stool` *global-option*... `status *stage-option*... (*field*|*property*)...


#### DESCRIPTION

Prints the specified status *field*s or properties. Default: print all fields.

Available fields:

* **apps**
  Application urls or this stage. Point your browser to these urls to access your application(s).
* **backstage**
  Absolute path of the backstage directory. Type string.
* **buildtime**
  Last modified date of the war files for this stage.
* **cpu**
  Cpu usage reported by Docker: percentage of this container's cpu utilisation relative to total system utilisation.
* **created-at**
  When the stage was created.
* **created-by**
  User who created this stage. Type string.
* **debugger**
  Debugger port or empty if not running with debugger.
* **directory**
  Absolute path of the stage directory. Type string.
* **disk**
  Disk space used for this stage in mb. Type number.
* **fitnesse**
  True if the fitnesse wiki is running, not the apps
* **id**
  Unique identifier for this stage. Type string.
* **jmx**
  Some jmx tool invocations for this stage.
* **last-modified-at**
  When this stage was last changed.
* **last-modified-by**
  The user that last maintained this stage, i.e. executed a Stool command like build, start, or stop.
* **mem**
  Memory usafe reported by Docker: percentage of memory limit actually used.
* **heap** 
  Heap usage for this stage as reported by Jmx. Percentage of used heap in relation to max heap.
* **others**
  Other urls this stage.
* **selected**
  `true` if this is the selected stage. Type boolean.
* **service**
  Java Service Wrapper process id or empty if the state is not up. 
* **suspend**
  `true` if running with suspend.
* **state**
  `down`, `sleeping` or `up`. Type string.
* **tomcat**
  Tomcat process id or empty if the stage is not up.
* **uptime**
  How long this stage is running.
* **type**
  `source` or `artifact`. Type string.
* **url**
  Url of this stage. Type string.

[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-list

List stages

#### SYNOPSIS

`stool` *global-option*... `list` *stage-option*... (*field*|*property*)...

#### DESCRIPTION

Displays status of all stages (or the stages specified by `-stage`) as a table. See the `status`
command for a list of available fields. Default fields/properties are `name state last-modified-by url directory`.

[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-cleanup

Cleanup a stage

#### SYNOPSIS

`stool` *global-option*... `cleanup` *stage-option*...

#### DESCRIPTION

Removes the Maven repository and rotates *.log into *.log.gz files.

[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-validate

Validate the stage

#### SYNOPSIS

`stool` *global-option*... `validate` *stage-option*... [`-email`] [`-repair`]

#### DESCRIPTION

Checks if the `expire` date of the stage has passes or the `quota` exceeded. If so, and if
`-repair` is specified, the stage is stopped (and also removed if expired for more than autoRemove days). And
if `-email` is specified, a notification mail is sent as configured by the notify property.

Also checks DNS settings.

Also performs log rotation: logs are gzipped and removed after 90 days.

Also checks Stool's locking system for stale locks and, if `-repair` is specified, removed them.

[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


## Setup

Prerequisites:
* Linux or Mac
* Java 8 or higher. This is prerequisite because Stool is implemented in Java 8, you need it to run Stool itself. 
  However, you can build and run your stages with any Java version you choose.

First of all: Stool is split into `stool` itself and the `dashboard`. The dashboard is optional, it
makes some of Stool's functionality available in a browser.

### Install Stool

You can install Stool on your machine either as a Debian package or from an application download:

Debian package:
* Download the latest `deb` file from [Maven Central](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22net.oneandone.stool%22%20AND%20a%3A%22main%22)
  (I'd like to have a public Debian repository instead, by I don't know who would host this for free).
* install it with `dpkg -i`

Application download: 
* Download the latest `application.sh` file from [Maven Central](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22net.oneandone.stool%22%20AND%20a%3A%22main%22)
* Make it executable, rename it to `stool` and add it to your $PATH.

Now check your installation: get `stool` - you should get a usage message.


### Isolated configuration

You'll normally configure Stool to maintain separate stages for separate users, everything lives in the respective user's home directory. 
This is called isolated configuration.

For every user that wants to use Stool:
* Optional: define an environment variable `STOOL_HOME` in your shell initialization (e.g. `~/.bash_profile`)
* Run `stool setup` to create Stool's home directory (default is `~/.stool`, override by defining `STOOL_HOME`).
* Adjust `~/.stool/config.json` to your needs: see [stool properties](#stool-properties)
* If you did not install the Debian package: source `~/.stool/shell.rc` in your shell initialization file (e.g. `~/.bash_profile`).


### Shared configuration

Shared setup is for you if you want to install Stool on a server, where multiple users work on a shared set of stages. 
Any user can create, modify and removed any stage on the system. Technically, this is done by configuring file system 
permissions to allow everybody in a group to access Stool. 

Instructions
* become `root`
* create a group `stool` and add all users you want to give access to the stages
* create a folder `sharedstages` somewhere on your disk; get on this folder:
  * `chgrp stool`
  * `chmod 2775` 
* for all `stool` users, define the environment variable `STOOL_HOME` and point it to `sharedstages/.stool`
* with the root user, follow the instructions for shared configuration
* optional: `chmod 422 $STOOL_HOME/config`

Note that by placing Stool home under the shared directory, all Stool configuration is shared as well.


### Cron job

You should setup a cronjob that runs

    stool validate -all -email -repair
    
every night. That will check for expired stages. And also rotate log files.


### Dashboard setup

* cd into Stool's home directory
* `stool create gav:net.oneandone.stool:dashboard:`*version* `.stool/system/dashboard`
* `nano ~/.stool/dashboard.properties` to specify 
  * `sso`: single sign on url to access the dashboard; missing or empty means: no authentication
  * `svnuser`: specifices value for `-svnuser` when invoking Stool
  * `svnpassword`: specifies value for `-svnpassword` when invoking Stool
* `stool port dashboard=8000`
* `stool start`


### Upgrading 

To upgrade from Stool versions before 3.4.0:
* uninstall the old version Stool; but do not remove any stages
* install 3.4.x as described above
* for every stage:
  * cd into the stage directory
  * run `stool import .`
  * Unfortunately, your previous stage configuration cannot be upgraded automatically, 
    you have to manually reconfigure your stage with `stool config`


## Directory Layout

... of $STOOL_HOME (default is `~/.stool`)

        |- config.json (Stool configuration)
        |- maven-settings.xml (to resolve dependencies if a user has no MAVEN_HOME)
        |- run
        |  |- locks       (holds all locking data)
        |  |- ports       (lists all allocated ports)
        |  '- sleep.json  (optional, holds sleeping stages)
        |- downloads      (caches Tomcat- and Service Wrapper downloads)
        |- templates      (Docker templates)
        |- logs           (Stool logs files)
        |  |- stool-YYmmDD.log(.gz)
        |  :
        |- system
        |  |- dashboard.properties (Dashboard configuration)
        |  |- dashboard (stage directory of the dashboard stage)
        |  :
        |- shell.rc (to initialized the users interactive shell)
        |- bash.complete
        '- backstages
           |- id (symlink to a backstage directory
           :

... of stage directories

        :
        :  (normal project files)
        :
        '- .backstage
          |- config.json      (stage configuration)
          |- .m2              (Maven repository for this stage)
          |- creator.touch    (created when stage is created, tracks created-by and created-at)
          |- modified.touch   (touched if Stool command modifies this stage)
          |- logs             (log file of running stage)
          |- container.id     (id of running container; does not exist otherwise)
          |- image.log        (Docker's image build output) 
          '- context          (context to build docker image)
              '- tomcat
                   |- tomcat.tar.gz
                   '- server.xml


Normal project files for source stages are simply the respective SCM files, for artifact stages they are:

     |- first-app
     |   |- ROOT.war    (artifact was downloaded)
     |   '– ROOT        (unpacked war, created by Tomcat)
     :       :
     |- second-app
     :   |- ROOT.war
         `- ROOT
             :

Bind mounts

    |- logs             (-> .backstage/logs)
    |- vhosts
    |    |- source-app   (-> exploded webapp, e.g. target/source-app-SNAPSHOT/)
    |    |- artifact-app (-> directory containing the artifact, e.g. artifact-app/) 
    :    :
