# Stool

## Introduction

Stool is a command line tool that provides a lifecycle for stages: create, build, start, stop, remove.
A stage is a Docker Container with web applications, built from sources or downloaded as artifacts.

### Quick Tour

For setup instructions, please read the respective section below. The following assumes that Stool is properly set up.

Here's an example, what you can do with Stool. 

Create a new stage by checking out application sources:

    stool create git:ssh://git@github.com/mlhartme/hellowar.git

Start it:

    stool start

To see the running application, point your browser to the url printed by the `start` command.

You can run

    stool status

to see if your application is running and to see the application urls.

To remove the stage, stop the application with

    stool stop

and wipe it from your disk with

    stool remove

You can create an arbitrary number of stages. Invoke

    stool list

to see what you have created and not yet removed. To switch to another stage, run

    stool select otherstage

Use 

    stool history
    
to see the Stool commands executed for the current stage.

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
  Where the stage is stored in your file system, it holds the source code or the artifacts of this stage.
  This is where you usually work with your stage. The directory is specified explicitly or implicitly when you create a
  stage. You can change the stage directory with `stool move`.
* **id**
  Unique identifier for a stage. The id is generated when creating a stage and it is never changed.
  However, users normally work with the stage name instead. You can see the id with `stool status id`.
* **name**
  User readable identification for a stage. Usually, the name is unique. The name of the selected stage 
  is shown in your shell prompt, you use it to switch between stages, and it's usually part of the application
  url(s). The name is determined explicitly or implicitly when you create a stage, in most cases it's simply the name 
  of the stage directory. You can change the name with `stool config name=`*newname*.
* **origin**
  Specifies where the web applications come from: A Subversion URL, a git url, Maven coordinates, or
  a file url pointing to a war file. Examples:
  
      git:ssh://git@github.com/mlhartme/hellowar.git
      svn:https://github.com/mlhartme/hellowar/trunk
      gav:net.oneandone:hellowar:1.0.2
      file:///home/mhm/foo.war

* **type**
  How the stage contains the application(s): source - checkout of project sources, or artifact - an artifact
  from a Maven repository. The stage origin implies the stage type.
* **state**
  one of
  * **down**
    stage is not running, applications cannot be used in a brower. This is the initial state after creation or after
    it was stopped.
  * **up**
    stage is running, applications can be accessed via application url(s). This is the state after successful
    start or restart.
  You can check the state with `stool status` or `stool list`.
* **last-modified-by**
  The user that last changed this stage.

### Selected stage and stage indicator

The selected stage is the stage the current working directory belongs to. In other words: your current working directory 
is the stage directory or a (direct or indirect) subdirectory of it. Unless otherwise specified, stage commands operate 
on the selected stage.

The stage indicator `{somestage}` is displayed in front of your shell prompt, it shows the name of the selected stage.

If you create a new stage, Stool changes the current working directory to the newly created stage directory. Thus, the new stage
becomes the selected stage. `stool select` changes the current working directory to the respective stage directory,
thus it is just a convenient way for cd'ing between stage directories.

The stage indicator is invisible if you have no stage selected; select a stage to see a stage indicator.

### Properties

Stool is configured via properties. A property is a key/value pair. Value has a type (string, number, date,
boolean, list (of strings), or map (string to string)). Stool distinguishes Stool properties and stage
properties. Stool properties are global settings that apply to all stages, they are usually adjusted by system
administrators (see [server configuration](#stool-server-configuration)). Stage properties configure the
respective stage only, every stage has its own set of stage properties. You can adjust stage properties 
with [stool config](#stool-config). You can adjust Stool properties by editing $STOOL_HOME/config.json.

Besides properties, every stage has status fields, you can view them with `stool status`. Status fields are similar to
properties, but they are read-only.


### Backstage

Every stage directory contains a backstage directory `.backstage` that stores Stool-related data, e.g. the stage 
properties or log files of the applications. The backstage directory is created when you create or import the stage. 
`$STOOL_HOME/backstages` contains a symlink *id*->*backstage* for every stage. Stool uses this to iterate all stages.

Stool removes backstage symlinks either explicitly when you run `stool remove`, or implicitly when it detects that
the symlink target directory has been removed. Stool checks for - and cleans - stale backstage links before every command.


### Stage Expiring

Every stage has an `expire` property that specifies how long the stage is needed. You can
see the expire date with `stool config expire`. If this date has passed, the stage is called
expired, and it is automatically stopped, a notification email is sent and you cannot start it again
unless you specify a new date with `stool config expire=`*yyyy-mm-dd*.

Depending on the `autoRemove` Stool property, an expired stage will automatically be removed after
the configured number of days. Stage expiring helps to detect and remove unused stages, which is crucial for
shared stages. If you receive an email notification that your stage has expired, please check if your stage
is still needed. If so, adjust the expire date. Otherwise, remove the stage.

### User defaults

Every users can define default values for various command line option by placing a properties file `.stool.defaults` in
her home directory. If this file exists, Stool uses the contained properties as default values for various options.
For example, a property `refresh.build=true` causes `stool refresh` to build a stage without
explicitly specifying the `-build` option. (Note: To override this default, use `stool refresh -build=false`).

Supported user default properties:

* **verbose**
  controls the `-v` option for every command
* **exception**
  controls the `-e` option for every command
* **auto.restart**
  controls the `-autorestart` option for every stage command
* **auto.stop**
  controls the `-autostop` option for every stage command
* **import.name**
  controls the `-name` option for the `import` command
* **import.max**
  controls the `-max` option for the `import` command
* **history.max**
  controls the `-max` option for the `history` command
* **history.details**
  controls the `-details` option for the `history` command
* **list.defaults**
  controls the `-defaults` option for the `list` command
* **status.defaults**
  controls the `-defaults` option for the `status` command
* **select.fuzzy**
  controls the `-fuzzy` option for the `select` command
* **svn.user** and **svn.password** 
  credentials the `-svnuser` and `-svnpassword` options for every Stool command.


### Dashboard

The dashboard is a special stage you can setup with
 
    stool create gav:net.oneandone.stool:dashboard:4.0.0-SNAPSHOT $STOOL_HOME/system/dashboard
    stool start

to control stages via browser. Technically, this is just a web frontend to run Stool commands.


## Commands

### stool

Stage tool

#### SYNOPSIS

`stool` *global-option*... [*command* *argument*...]

#### DESCRIPTION

Stool is a command line tool to manage stages. After creating a stage, you can build, start and apps for it.
*command* defaults to `help`.

#### Commands

[//]: # (ALL_SYNOPSIS)

`stool` *global-option*... [*command* *argument*...]


`stool` *global-option*... `help` [*command*]


`stool` *global-option*... `version`


`stool` *global-option*... `auth` *server*


`stool` *global-option*... `create` [`-project` *project* ] nameAndServer [*key*`=`*value*...]



`stool` *global-option*... `attach` [`-project` *project* ] stage


`stool` *global-option*... `detach` [`-project` *project* ]


`stool` *global-option*... `build` [`-project` *project* ][`-nocache`][`-keep` *keep*][`-restart`] [*war* ...] [*key*`=`*value*]



`stool` *global-option*... *stage-command* [`-all`|`-stage` *predicate*] [`-fail` *mode*] *command-options*...


`stool` *global-option*... `remove` *stage-option*... [`-stop`] [`-batch`]


`stool` *global-option*... `start` *stage-option*... [`-tail`] [`-nocache`]


`stool` *global-option*... `stop` *stage-option*... [*app*...]


`stool` *global-option*... `restart` *stage-option*... [*app*[`:`*idx*] ...]



`stool` *global-option*... `history` *stage-option*... [`-details`] [`-max` *n*] 


`stool` *global-option*... `config` *stage-option*... (*key* | *value*)...


`stool` *global-option*... `status *stage-option*... (*field*|*property*)...



`stool` *global-option*... `app *stage-option*...



`stool` *global-option*... `list` *stage-option*... (*field*|*property*)...


`stool` *global-option*... `cleanup` *stage-option*...


`stool` *global-option*... `validate` *stage-option*... [`-email`] [`-repair`]

[//]: # (-)

#### Global options

* **-v** enables verbose output
* **-e** prints stacktrace for all errors


#### Stool Server Configuration

The following environment variables can be used to configure Stool server. This is usually done by adjusting `$STOOL_HOME/server/server.yml`. 


* **ADMIN** 
  Email of the person to receive validation failures and exception mails. Empty to disable these emails.
  Type string, default empty. Example: `Max Mustermann <max@mustermann.org>`.
* **APP_PROPERTIES_FILE** and **APP_PROPERTIES_PREFIX** 
  Where in a war file to locate the properties to configure build arguments.
* **AUTO_REMOVE**
  Days to wait before removing an expired stage. -1 to disable this feature. Type number, default -1. 
* **DISK_QUOTA**
  Mb of disk spaces available for the root file system of all apps in all stages. The sum of all disk space reserved for all apps of all stages
  cannot exceed this number. 0 disables this feature. Type number, default 0.
* **DOCKER_HOST**
  Fully qualified hostname used to refer to this machine in application urls and emails. Type string.
* **ENVIRONMENT** 
  Default environment variables to set automatically when starting apps, can be overwritten by the start command.
* **LDAP_CREDENTIALS**
  Password for Ldap authentication. Ignored if ldap is disabled. Type string, default empty.
* **LDAP_PRINCIPAL**
  User for Ldap authentication. Ignored if ldap is disabled. Type string, default empty.
* **LDAP_UNIT**
  Specifies the "organizational unit" to search for users. Ignored if ldap is disabled. Type string, default empty.
* **LDAP_URL**
  Ldap url for user information. Empty string to disable ldap. Type string, default empty.
* **LOGLEVEL**
  for server logging. Type string, default INFO.
* **MAIL_HOST**
  Smtp Host name to deliver emails. Empty to disable. Type string, default empty.
* **MAIL_USERNAME**
  Username for mailHost. Type string, default empty;
* **MAIL_PASSWORD**
  Password for mailHost. Type string, default empty.
* **MEMORY_QUOTA**
  Max memory that stages may allocate. 0 to disable. Type number, default 0.
* **PORT_FIRST**
  First port available for stages. Has to be an even number >1023. Type number, default 9000.
* **PORT_LAST**
  Last port available for stages. Has to be an odd number >1023. Type number, default 9999.
* **SECRETS**
  Absolute path of the secrets directory on the stage host. Type string. TODO: determine automatically.
* **REGISTRY_NAMESPACE**
  Prefix for all stage tags. Type string.
* **VHOSTS**
  `true` to create urls with vhosts for app and stage name.
  `false` to create urls without vhosts. (Note that urls always contain the port to
  distinguish between stages). Type boolean. If you want to enable vhosts you have to make sure you
  have the respective DNS * entries for your machine.


#### Ports

Stool allocates ports from the range $PORT_FIRST ... $POST_LAST. To choose a port for a given Stage, Stool computes a hash between
portFirst and portLast. If this port if already allocated, the next higher port is checked (with roll-over to portFirst if necessary).

#### Environment

`STOOL_OPTS` to configure the underlying JVM.
 

#### See Also

Homepage: https://github.com/mlhartme/stool

Documentation: https://github.com/mlhartme/stool/blob/master/main/documentation.md

Invoke `stool help` *command* to get help for the specified command.


## General commands

### stool-help 

Display man page

#### SYNOPSIS

`stool` *global-option*... `help` [*command*]

#### DESCRIPTION

Prints help about the specified *command*. Or, if *command* is not specified, prints help about Stool.

### stool-version 

Display version info

#### SYNOPSIS

`stool` *global-option*... `version`

#### DESCRIPTION

Prints Stool's version info. Use the global `-v` option to get additional diagnostic info.


### stool-auth

Authenticate to a server.

#### SYNOPSIS

`stool` *global-option*... `auth` *server*

#### DESCRIPTION

Asks for username/password to authenticate against ldap. If authentication succeeds, the respective *server* is asked for an api token
that will be stored and used for future access to this server.


## Project commands

### stool-create

Create a new stage.

#### SYNOPSIS

`stool` *global-option*... `create` [`-project` *project* ] nameAndServer [*key*`=`*value*...]


#### DESCRIPTION

Creates a new stage one the specified *server*, with the specified *name*. 

*project* specifies the directory to attach the new stage to. Default is the current directory.

The new stage is configured with the specified *key*/*value* pairs. Specifying a *key*/*value* pair is equivalant to running 
[stool config](#stool-config) with these arguments.


#### Examples

Create a source stage from svn: `stool create hello@localhost`

### stool-attach

Attaches a project to stage.

#### SYNOPSIS

`stool` *global-option*... `attach` [`-project` *project* ] stage

#### DESCRIPTION

Attaches the specified *project* (default is the current directory) to the specified *stage*. 
*project* is a directory. Technically, a `.backstage` file will be created that stores the stage.
*stage* has the form *name*`@`*server*.

If the project already has a stage attached, this old attachment is overwritten. 

### stool-detach

Detaches a project to stage.

#### SYNOPSIS

`stool` *global-option*... `detach` [`-project` *project* ]

#### DESCRIPTION

Removes the attachment of the specified *project* without modifying the stage itself.


### stool-build

Build a project.

#### SYNOPSIS

`stool` *global-option*... `build` [`-project` *project* ][`-nocache`][`-keep` *keep*][`-restart`] [*war* ...] [*key*`=`*value*]


#### DESCRIPTION

Builds the specified wars (if not specified: all wars) on the associated stage. The war is uploaded to the respective server and a 
Docker build is run the the specified build arguments. Available build argument depend on the template being used. More build arguments
are loaded from a properties file in the war. You can see the build argument actually used with `stool app`.

*keep* specifies the nummer of docker images that will not be exceeded, default is 3. I.e. if you already have 3 images and run `build`, 
the oldest unreferenced of the will be removed. Unreferenced means it's not used for the current container.


## Stage Commands

Most Stool commands are stage commands, i.e. they operate on one or multiple stages. Typical
stage commands are `status`, `start`, and `stop`. Note that `create` is not a stage command 
because it does not initially have a stage to operate on (although it results in a new (and selected) 
stage).

All stage commands support stage options, get `stool help stage-options` for documentation.

### stool-stage-options

Options available for all stage commands

#### SYNOPSIS

`stool` *global-option*... *stage-command* [`-all`|`-stage` *predicate*] [`-fail` *mode*] *command-options*...

#### Selection options

By default, stage commands operate on the attached stage (as shown in the stage indicator). You can change this by specifying one of the
following selection option:

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


The most basic predicate is a simple `NAME`. It performs a substring match on the stage name. This is handy to run one command for a stage 
without attaching it.

Next, a predicate *FIELD*`=`*VALUE* matches stages who's status field has the specified value.
*PROPERTY*`=`*VALUE* is similar, it matches stage properties.

#### Failure mode

Since stage commands operate on an arbitrary number of stages, you might want to specify what to do if the command
fails for some of them. That's what `-fail` *mode* is for.

Mode `normal` reports problems immediately and aborts execution, Stool does not try to run the command 
on remaining matching stages. This is the default.

`after` reports problems after the command was invoked on all matching stages.

`never` is similar to `after`, but reports warnings instead of errors (and thus, Stool always returns with exit code 0).

#### Examples

`stool status -stage foo` prints the status of stage `foo`.

`stool start -all -fail after` starts all stages. Without `-fail after`, the command would abort
after the first stage that cannot be started (e.g. because it's already running).

`stool stop -stage up=true` stops all stages currently up, but aborts immediately if one stage fails to stop.


### stool-remove

Remove a stage

#### SYNOPSIS

`stool` *global-option*... `remove` *stage-option*... [`-stop`] [`-batch`]

#### Description

Removes the stage, i.e. deletes it from the respective server. This includes docker images, containers, and log files.
If the current project it attached to this stage, the attachment is removed as well.

Reports an error if the stage is up. In this case, stop the stage first or invoke with `-stop`. 

Before actually removing anything, this command asks if you really want to remove the stage. You can suppress this interaction 
with the `-batch` option.

[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-start

Start a stage

#### SYNOPSIS

`stool` *global-option*... `start` *stage-option*... [`-tail`] [`-nocache`]

#### Description

Creates a Docker image based on the current template and starts it. Depending on your application(s), startup may take a while.

Startup is refused if your stage has expired. In this case, use `stool config expire=`*newdate*
to configure a new `expire` date.

Startup is also refused if the disk quota exceeded. In this case, delete some unused files, try `stool cleanup`, or use 
`stool config quota=`*n*.

Use the `-tail` option to get container output printed to the console. Press ctrl-c to stop watching output, 
the container will continue to run.

Use the `-nocache` option to disable Dockers image cache. This will build the image from scratch, which is useful e.g.
to re-generate certificates or to re-load secrets.

`start` generates a Docker context directory in `$STAGE/.backstage/context`. This directory is populated with all file
from the template directory. Template files with the `.fm` extension are passed through the FreeMarker template engine
and the result is writted to a file without this extension. Image build output written
to `$STAGE/.backstage/image.log`. The docker container is startet as the current user, not as root. The following bind
mounts are created:
* `/logs` -> `$stage/.backstage/logs`
* for source stages: `/vhosts/source-app` -> exploded webapp, e.g. `$stage/target/source-app-SNAPSHOT`
* for artifact stages: `/vhosts/artifact-app` -> directory containing the artifact, e.g. `$stage/artifact-app` 


The Docker template is configurable `stool config template=`*template*. If you change it, you have to restart the stage.
You can also adjust files in the template directory; make sure to restart the stage to reflect your changes.



[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-stop

Stop a stage

#### SYNOPSIS

`stool` *global-option*... `stop` *stage-option*... [*app*...]

#### DESCRIPTION

Stops the specifed apps (if none is specified: all running apps). 

This command sends a "kill 15" to the root process of the container. If that's not successful within 300 seconds, the process is forcibly 
terminated with "kill 9". If shutdown is slow, try to debug the apps running in this stage and find out what's slow in their kill 15 
signal handling. 


[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-restart

Restart a stage

#### SYNOPSIS

`stool` *global-option*... `restart` *stage-option*... [*app*[`:`*idx*] ...]


#### DESCRIPTION

Shorthand for `stool stop *app*... && stool start *app*:*idx*`.

[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-history

Display command invoked on this stage

#### SYNOPSIS

`stool` *global-option*... `history` *stage-option*... [`-details`] [`-max` *n*] 

#### DESCRIPTION

Prints the Stool commands that affected the stage. Specify `-details` to also print command output. Stops after the 
specified max number of commands (*n* defauls is 50).

[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-config

Manage stage properties

#### SYNOPSIS

`stool` *global-option*... `config` *stage-option*... (*key* | *value*)...

#### DESCRIPTION

This command gets or sets stage [properties](#properties). Caution: `config` does not deal with Stool properties, see `stool help` for that.

When invoked without arguments, all stage properties are printed.

When invoked with one or more *key*s, the respective properties are printed.

When invoked with one or more assignments, the respective properties are changed.

Property values may contain `{}` to refer to the previous value. You can use this, e.g., to append to a property:
`stool config "comment={} append this"`.

If you want to set a property to a value with spaces, you have to use quotes around the key-value pair.
Otherwise, the Stool does not see what belongs to your value.

If you change a property, you have to run the necessary re-builds or re-starts to make the changes
effective. E.g. if you change `memory`, you have to run `stool restart` to make the change effective.

Properties have a type: boolean, number, date, string, list of strings, or map of strings to strings.

Boolean properties have the values `true` or `false`, case sensitive.

Date properties have the form *yyyy-mm-dd*, so a valid value for `expire` is - e.g. -`2016-12-31`. Alternatively, 
you can specify a number which is translated into the date that number of days from now (e.g. `1` means tomorrow).

List properties (e.g. `select`) are separated by commas, whitespace before and after an item is ignored.

Map properties (e.g. `macros`) separate entries by commas, whitespace before and after is ignored.
Each entry separates key and value by a colon. Example `foo:bar, key:value`

[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


#### Available stage properties

Note that the default values below might be overwritten by Stool defaults on your system.


* **comment**
  Arbitrary comment for this stage. Stool only stores and displays this value, it has no effect. Type string.
* **expire**
  Defines when this stage [expires](#stage-expiring). Type date.
* **notify**
  List of email addresses or `@last-modified-by` or `@created-by` to send notifications about
  this stage. Type list. Default value: `@created-by`.

#### Examples

`stool config comment` prints the current `comment` value.

`stool config comment=42` sets the comment to 42.


### stool-status

Display stage status

#### SYNOPSIS

`stool` *global-option*... `status *stage-option*... (*field*|*property*)...


#### DESCRIPTION

Prints the specified status *field*s or properties. Default: print all fields.

Available fields:

* **name**
* **apps**
  App urls of this stage. Point your browser to one fo them access your app(s).
* **running**
* **urls**
* **created-by**
  User who created this stage.
* **created-at**
  When this stage was created.
* **last-modified-by**
  User who last modified this stage.
* **last-modified-at**
  Last modified date of this stage.

[//]: # (include stageOptions.md)

Note: This is a stage command, get `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-app

Display app status

#### SYNOPSIS

`stool` *global-option*... `app *stage-option*...


#### DESCRIPTION


Available fields:

* **apps**
  Application urls of this stage. Point your browser to one fo them access your application(s).
* **backstage**
  Absolute path of the backstage directory. Type string.
* **buildtime**
  Last modified date of the war files for this stage.
* **container**
  container id if the stage is up; otherwise empty
* **cpu**
  Cpu usage reported by Docker: percentage of this container's cpu utilisation relative to total system utilisation.
* **created-at**
  When the stage was created.
* **created-by**
  User who created this stage. Type string.
* **directory**
  Absolute path of the stage directory. Type string.
* **disk**
  Disk space used for by stage directory mb. Does not include disk space used for Docker image and container. Type number.
* **container-disk**
  Disk space used for by running container in mb. This does not include the size of the underlying image, it's just the size of the RW layer. Type number.
* **id**
  Unique identifier for this stage. Type string.
* **last-modified-at**
  When this stage was last changed.
* **last-modified-by**
  The user that last maintained this stage, i.e. executed a Stool command like start, or stop.
* **mem**
  Memory usage reported by Docker: percentage of memory limit actually used. Note that this memory also includes 
  disk caches, so a high value does not necessarily indicate a problem. Type number.
* **selected**
  `true` if this is the selected stage. Type boolean.
* **state**
  `down` or `up`. Type string.
* **uptime**
  How long this stage is in state `up`. Empty if stage is not up. Type string.
* **type**
  `source` or `artifact`. Type string.
* **origin**
  Origin of this stage. Type string.
  

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

Rotates *.log into *.log.gz files.

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

Also checks if the docker container for this stage is in stage running. 

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
* Docker 18.03 or newer

Stool is split into `stool` itself and the `dashboard`. The dashboard is optional, it makes some of Stool's functionality available in a browser.

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


### User configuration

Every user has it's own set of Stages.

For every user that wants to use Stool:
* Optional: define an environment variable `STOOL_HOME` in your shell initialization (e.g. `~/.bash_profile`)
* Run `stool setup` to create Stool's home directory (default is `~/.stool`, override by defining `STOOL_HOME`).
* Adjust `~/.stool/server.yml` to your needs: see [server configuration](#stool-server-configuration)
* If you did not install the Debian package: source `~/.stool/shell.rc` in your shell initialization file (e.g. `~/.bash_profile`).
* run `stool validate` to check you setup


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

There's no automatic upgrade from Stool 4 to Stool 5. You have to re-create all stages.


## Directory Layout

... of $STOOL_HOME (default is `~/.stool`)

        |- version                   (client version that created this directory)
        |- shell.inc
        |- server.yml                (docker-comppose file to start local server)
        |- servers.json              (list of Stool servers to talk to)
        '- server                    (empty if there's no local server)
           |- version                (server version that created this directory)
           |- config.json            (Stool configuration)
           |- users.json             (only if authentication is enabled)
           |- templates              (Docker templates; may be a symlink to a directory of your choice)
           |- logs                   (Stool log files)
           |  :
           '- stages
              |- name                (directory for the respective stage)
              :  |- config.json      (stage configuration)
                 '- logs
                      |- app1        (log file of appsrunning stage)
                          :

... of project directories

        :
        :  (normal project files)
        :
        '- .backstage             (file containing the attached stage)


### Building Stool


You need Java 8 and Docker to build Stool
