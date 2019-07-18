# Stool

## Introduction

Stool is a tool to manage stages: create, build, start, stop, remove. A stage is a set of Web applications

### Quick Tour

For setup instructions, please read the respective section below. The following assumes that Stool is properly set up.

Here's an example, what you can do with Stool. 

Enter a project directory with a Web application of your - or get a sample application with

    git clone ssh://git@github.com/mlhartme/hellowar.git hellowar
    cd hellowar
        
Create a new stage by checking out application sources:

    stool create hello@localhost

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

to see what you have created and not yet removed. 

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
* $PROJECT denotes the project directory currently used 


## Concepts

### Stage

A stage is a set of apps, where each app is typically a Tomcat servlet container (http://tomcat.apache.org) with a Java web application 
(https://en.wikipedia.org/wiki/Java_Servlet). Apps are stored as images, and you can have different images for different versions of the 
app. Technially, any image is a Docker image, and if you start an app, the respective Docker container is created.

* **name**
  Unique identifier and user readable identification for a stage. The name of the attached stage is shown in your shell prompt, and it's 
  usually part of the application url(s). The name is defined when you create a stage, and you cannot change it later.
* **running**
  true if at least on app of the stage is running; otherwise false. Running means the container is up and you can access the stage in
  your browser. You can check the state with `stool status` or `stool list`.
* **last-modified-by**
  The user that last changed this stage.


### Project

The current project is a the base directory user for various Stool command, in particular for building. In most cases, the current project
has a stage attached. 

You can specify the current project with the `-project` command line option; if not specified the current directory and its parent 
directories are searched for a `.backstage` directory. If successful, that's the project; otherwise, the current directory is the project 
(without stage attached).


### App

A set of docker images with the same name.

### Image

* **origin**
  Specifies where the image came from: A Subversion URL, a git url, Maven coordinates, or
  a file url pointing to a war file. Examples:
  
      git:ssh://git@github.com/mlhartme/hellowar.git
      svn:https://github.com/mlhartme/hellowar/trunk
      gav:net.oneandone:hellowar:1.0.2

### Attached stage and stage indicator

The attached stage associated with the current project. Unless otherwise specified, stage commands operate on the attached stage.

The stage indicator `{somestage@server}` is displayed in front of your shell prompt. It shows the name of the attached stage and the server
hosting it.

If you create a new stage, Stool attaches the current project to the newly created stage. If you `cd` into a different project, the
stage indicator changes accordingly. You can explicitly change the attached stage with `stool attach` and `stool detach`. The stage indicator 
is invisible if the current project has no stage attached.


### Properties

Stool is configured via properties. A property is a key/value pair. Value has a type (string, number, date, boolean, list (of strings), 
or map (string to string)). Stool distinguishes server properties and stage properties. Server properties are global settings that apply to 
all stages, they are usually adjusted by system administrators (see [server configuration](#stool-server-configuration)). Stage properties 
onfigure the respective stage only, every stage has its own set of stage properties. You can adjust stage properties 
with [stool config](#stool-config). 

Besides properties, every stage has status fields, you can view them with `stool status`. Status fields are similar to
properties, but they are read-only.

Besides properties, stages have build options and environment variables for every application image. Use `stool app` to see the current
values.
 

### Backstage

Projects can optionally have a `.backstage` file. If this file exists, it defines the attached stage; otherwise, the 
project has no attached stage. The backstage file is created when you create a stage, it's created or modified by the `attach` command,
and it's removed by `detach`. 

### Stage Expiring

Every stage has an `expire` property that specifies how long the stage is needed. You can see the expire date with `stool config expire`. 
If this date has passed, the stage is called expired, and it is automatically stopped, a notification email is sent and you cannot start it 
again unless you specify a new date with `stool config expire=`*yyyy-mm-dd*.

Depending on the `autoRemove` Stool property, an expired stage will automatically be removed after the configured number of days. Stage 
expiring helps to detect and remove unused stages, which is handy (and sometimes even crucial) if you are not the only user of a server. 
If you receive an email notification that your stage has expired, please check if your stage is still needed. If so, adjust the expire date. 
Otherwise, remove the stage.


### Dashboard

The dashboard is the UI for Stool server. 


## Commands

### stool

Stage tool

#### SYNOPSIS

`stool` *global-option*... [*command* *argument*...]

#### DESCRIPTION

`stool` is a command line tool to manage stages. After creating a stage, you can build, start and apps for it.
*command* defaults to `help`. Stages can be managed on any machine that runs a Stool server daemon. Technically, `stool` is a 
rest client for Stool server, and Stool server wraps a Docker Engine.


#### Commands

[//]: # (ALL_SYNOPSIS)

`stool` *global-option*... [*command* *argument*...]


`stool` *global-option*... `help` [*command*]


`stool` *global-option*... `version`


`stool` *global-option*... `auth` [*server*]


`stool` *global-option*... *project-command* [`-project` *project*] *command-options*... *command-arguments*...



`stool` *global-option*... `create` *project-option*...  *name*`@`*server* [*key*`=`*value*...]



`stool` *global-option*... `attach` *project-option*... *stage*


`stool` *global-option*... `detach`


`stool` *global-option*... `build` *project-option*... [`-nocache`][`-keep` *keep*][`-restart`] [*war*...] [*key*`=`*value*...]



`stool` *global-option*... *stage-command* [`-all`|`-stage` *predicate*] [`-fail` *mode*] *command-options*...



`stool` *global-option*... `remove` *stage-option*... [`-stop`] [`-batch`]


`stool` *global-option*... `start` *stage-option*... [-http *port*] [-https *port*] [*key*`=`*value*...][*app*[`:`*tag*] ...]


`stool` *global-option*... `stop` *stage-option*... [*app*...]


`stool` *global-option*... `restart` *stage-option*... [*app*[`:`*tag*] ...]



`stool` *global-option*... `history` *stage-option*... [`-details`] [`-max` *max*] 


`stool` *global-option*... `config` *stage-option*... (*key* | *value*)...


`stool` *global-option*... `status *stage-option*... (*field*|*property*)...



`stool` *global-option*... `app *stage-option*...



`stool` *global-option*... `list` *stage-option*... (*field*|*property*)...


`stool` *global-option*... `tunnel` *stage-option*... *app* *port* [*local*]


`stool` *global-option*... `ssh` *stage-option*... *app*


`stool` *global-option*... `validate` *stage-option*... [`-email`] [`-repair`]

[//]: # (-)

#### Global options

* **-v** enables verbose output
* **-e** prints stacktrace for all errors


#### Stool Server Configuration

The following environment variables can be used to configure Stool server in `$STOOL_HOME/server/server.yml`. 


* **ADMIN** 
  Email of the person to receive validation failures and exception mails. Empty to disable these emails.
  Type string, default empty. Example: `Max Mustermann <max@mustermann.org>`.
* **APP_PROPERTIES_FILE** and **APP_PROPERTIES_PREFIX** 
  Where in a war file to locate the properties file to configure build arguments.
* **AUTO_REMOVE**
  Days to wait before removing an expired stage. -1 to disable this feature. Type number, default -1. 
* **DISK_QUOTA**
  Mb of disk spaces available for the read/write layer of all running apps. The sum of all disk space reserved for all apps of all stages
  cannot exceed this number. 0 disables this feature. Type number, default 0.
* **DEFAULT_EXPIRE**
  Defines the number of days to expire new stages (0 for never). Type number, default 0.
* **DOCKER_HOST**
  Fully qualified hostname of this machine. Used in application urls and emails. Type string.
* **ENGINE_LOG**
  to log all traffic between server and docker daemon. CAUTION: enabling writes huge amounts of data if you have large war files.
  Type boolean, default value is false.
* **ENVIRONMENT** 
  Default environment variables set automatically when starting apps, can be overwritten by the `start` command. Type map, default empty.
* **JMX_USAGE**
  How to invoke a jmx client in your environment. Type string, default "jconsole %s".  
* **LDAP_CREDENTIALS**
  Password for Ldap authentication. Ignored if ldap is disabled. Type string, default empty.
* **LDAP_PRINCIPAL**
  User for Ldap authentication. Ignored if ldap is disabled. Type string, default empty.
* **LDAP_UNIT**
  Specifies the "organizational unit" to search for users. Ignored if ldap is disabled. Type string, default empty.
* **LDAP_URL**
  Ldap url for user information. Empty string to disable ldap. Type string, default empty.
* **LDAP_SSO**
  Url for ui single sign on. Type string, default empty.
* **LOGLEVEL**
  for server logging. Type string, default INFO. Example value: DEBUG.
* **MAIL_HOST**
  Smtp Host name to deliver emails. Empty to disable. Type string, default empty.
* **MAIL_USERNAME**
  Username for MAIL_HOST. Type string, default empty;
* **MAIL_PASSWORD**
  Password for mailHost. Type string, default empty.
* **MEMORY_QUOTA**
  Max memory that all apps may allocate. 0 to disable. Type number, default 0.
* **PORT_FIRST**
  First port available for stages. Type number, default 9000.
* **PORT_LAST**
  Last port available for stages. Type number, default 9999.
* **REGISTRY_NAMESPACE**
  Prefix for all stage repository tags. Has to be all lower case (because it's used for Docker tags which have to be lower case). Type string.
* **VHOSTS**
  `true` to create urls with subdomains for app and stage name.
  `false` to create urls without subdomains. (Note that urls always contain the port to distinguish between stages). Type boolean. 
  If you want to enable vhosts you need the respective DNS * entries for your machine.
  

#### Ports

Stool allocates ports from the range $PORT_FIRST ... $POST_LAST. To choose a port for a given Stage, Stool computes a hash in this range.
If this port if already allocated, the next higher port is checked (with roll-over to portFirst if necessary).


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

Display help about the specified *command*. Or, if *command* is not specified, display general `stool` help.

### stool-version 

Display version info

#### SYNOPSIS

`stool` *global-option*... `version`

#### DESCRIPTION

Prints version info. Use the global `-v` option to get additional diagnostic info.


### stool-auth

Authenticate to server(s)

#### SYNOPSIS

`stool` *global-option*... `auth` [*server*]

#### DESCRIPTION

Asks for username/password to authenticate against ldap. If authentication succeeds, the respective *server* (if not specified: all servers
that need authentication) is asked for an api token that will be stored and used for future access to this server.


## Project commands

Stage commands operate on a project, which is usually a checkout of the application you're working on. 


### stool-project-options

Options available for all project commands

#### SYNOPSIS

`stool` *global-option*... *project-command* [`-project` *project*] *command-options*... *command-arguments*...


#### DESCRIPTION

*project* specifies the project to work on. If not specified the project is determined implicitly from the current working directory.


### stool-create

Create a new stage

#### SYNOPSIS

`stool` *global-option*... `create` *project-option*...  *name*`@`*server* [*key*`=`*value*...]


#### DESCRIPTION

Creates a new stage with the specified *name* one the specified *server* and attaches it to the project. 
Reports an error if the server already hosts a stage with this name. 

The new stage is configured with the specified *key*/*value* pairs. Specifying a *key*/*value* pair is equivalent to running 
[stool config](#stool-config) with these arguments.

The name must contain only lower case ascii characters or digit. Otherwise it's rejected because it would cause problems with
urls or docker tags that contain the name.


#### Examples

Create a stage `hello` on server `localhost`: `stool create hello@localhost`

[//]: # (include projectOptions.md)

Note: This is a project command, use `stool help project-options` to see available [project options](#stool-project-options)
[//]: # (-)

### stool-attach

Attach a project to stage

#### SYNOPSIS

`stool` *global-option*... `attach` *project-option*... *stage*

#### DESCRIPTION

Attaches the specified *stage* to the project. If the project already has a stage attached, this old attachment is overwritten. 

Technically, `attach` creates a `.backstage` file that stores the stage. *stage* has the form *name*`@`*server*.

[//]: # (include projectOptions.md)

Note: This is a project command, use `stool help project-options` to see available [project options](#stool-project-options)
[//]: # (-)


### stool-detach

Detach a stage from a project

#### SYNOPSIS

`stool` *global-option*... `detach`

#### DESCRIPTION

Removes the attached stage from the project without modifying the stage itself. Technically, `detach` simplify removes the `.backstage` file.


[//]: # (include projectOptions.md)

Note: This is a project command, use `stool help project-options` to see available [project options](#stool-project-options)
[//]: # (-)


### stool-build

Build a project

#### SYNOPSIS

`stool` *global-option*... `build` *project-option*... [`-nocache`][`-keep` *keep*][`-restart`] [*war*...] [*key*`=`*value*...]


#### DESCRIPTION

Builds the specified wars (if not specified: all wars of the project) on the attached stage. The war is uploaded to the respective server 
and a Docker build is run with the build arguments (specified by *key*`=`*value* arguments). Available build argument depend on the template 
being used, you can see them in the error message if you specify an unknown build argument. Default build arguments are loaded from a 
properties file in the war. You can see the build argument actually used for an image with `stool app`.

You can build a stage while it's running. In this case, specify `-restart` to stop the currently running image and that the newly build one.
Or use `stool restart`. 

*keep* specifies the number of images that will not be exceeded for this application, default is 3. I.e. if you already have 3 images 
and run `build`, the oldest unreferenced image the will be removed. Unreferenced means it's not currently running in a container.

[//]: # (include projectOptions.md)

Note: This is a project command, use `stool help project-options` to see available [project options](#stool-project-options)
[//]: # (-)


## Stage Commands

Most Stool commands are stage commands, i.e. they operate on one or multiple stages. Typical stage commands are `status`, `start`, 
and `stop`. All stage commands support the same stage options, see `stool help stage-options` for documentation.


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

Removes the stage, i.e. deletes it from the respective server. This includes images, containers and log files.
If the current project it attached to this stage, the attachment is removed as well.

Reports an error if the stage is up. In this case, stop the stage first or invoke the command with `-stop`. 

Before actually removing anything, this command asks if you really want to remove the stage. You can suppress this interaction 
with the `-batch` option.

[//]: # (include stageOptions.md)

Note: This is a stage command, use `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-start

Start a stage

#### SYNOPSIS

`stool` *global-option*... `start` *stage-option*... [-http *port*] [-https *port*] [*key*`=`*value*...][*app*[`:`*tag*] ...]

#### Description

Starts the specified *app*s (if not specified: all that are not running yet) with the environment arguments specified
by *key*=*value* arguments. *app* can be specified with a tag to determin the actual image to be started; if not specified, the latest
tag is used. Use `stool app` to see available images.

If you specify http or https options, the respective port will be used for the application. Otherwise, those ports are chosen automatically.

Before starting an app, Stool checks if it has previously been started. If so, the respective container is removed.

Startup is refused if the user who built the image does not have access to all fault projects of the image.

Startup is refused if your stage has expired. In this case, use `stool config expire=`*newdate* to configure a new `expire` date.

Startup is also refused if the disk or memory quota exceeded. In this case, stop some other stages.

und 

[//]: # (include stageOptions.md)

Note: This is a stage command, use `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-stop

Stop a stage

#### SYNOPSIS

`stool` *global-option*... `stop` *stage-option*... [*app*...]

#### DESCRIPTION

Stops the specified apps (if none is specified: all running apps). 

This command sends a "kill 15" to the root process of the container. If that's not successful within 300 seconds, the process is forcibly 
terminated with "kill 9". If shutdown is slow, try to debug the apps running in this stage and find out what's slow in their kill 15 
signal handling. 


[//]: # (include stageOptions.md)

Note: This is a stage command, use `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-restart

Restart a stage

#### SYNOPSIS

`stool` *global-option*... `restart` *stage-option*... [*app*[`:`*tag*] ...]


#### DESCRIPTION

Shorthand for `stool stop *app*... && stool start *app*:*tag*`.

[//]: # (include stageOptions.md)

Note: This is a stage command, use `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-history

Display commands invoked on this stage

#### SYNOPSIS

`stool` *global-option*... `history` *stage-option*... [`-details`] [`-max` *max*] 

#### DESCRIPTION

Prints the `stool` commands that affected the stage. Specify `-details` to also print command output. Stops after the 
specified *max* number of commands (defauls is 50).

[//]: # (include stageOptions.md)

Note: This is a stage command, use `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-config

Manage stage properties

#### SYNOPSIS

`stool` *global-option*... `config` *stage-option*... (*key* | *value*)...

#### DESCRIPTION

This command gets or sets stage [properties](#properties). 

Caution: `config` does not deal with build argumetn, see `stool build` for that. And: it does not deal with environment variables,
see `stool start` for that. And finally: it does not deal with Stool properties, see `stool help` for that.

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

Note: This is a stage command, use `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


#### Available stage properties

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
  Name of the stage.
* **apps**
  App names of this stage. 
* **running**
  Currently running images of this stage.
* **urls**
  Urls for all apps of this stage. Point your browser to one fo them access your app(s).
* **created-by**
  User who created this stage.
* **created-at**
  When this stage was created.
* **last-modified-by**
  User who last modified this stage.
* **last-modified-at**
  Last modified date of this stage.

TODO: what else?


[//]: # (include stageOptions.md)

Note: This is a stage command, use `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-app

Display app status

#### SYNOPSIS

`stool` *global-option*... `app *stage-option*...


#### DESCRIPTION

Available fields: TODO

* **apps**
  Application urls of this stage. Point your browser to one fo them access your application(s).
* **backstage**
  Absolute path of the backstage directory. Type string.
* **buildtime**
  Last modified date of the war files for this stage.
* **container**
  container id if the stage is running.
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

Note: This is a stage command, use `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-list

List stages

#### SYNOPSIS

`stool` *global-option*... `list` *stage-option*... (*field*|*property*)...

#### DESCRIPTION

Displays status of all stages (or the stages specified by `-stage`) as a table. See the `status`
command for a list of available fields. Default fields/properties are `name state last-modified-by url directory`.

[//]: # (include stageOptions.md)

Note: This is a stage command, use `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


### stool-tunnel

Start an ssh tunnel

#### SYNOPSIS

`stool` *global-option*... `tunnel` *stage-option*... *app* *port* [*local*]

#### DESCRIPTION

Starts an ssh tunnel to the specified *port* of the specified *app*. *port* can be `debug` or `jmx`. Reports an error if you have no
permission to access this port. TODO: currently, only the user who built the app is permitted. Press Ctrl-C to forcibly close the tunnel.

The tunnel is refused if the current user does not have access to all fault projects of the image.

Use *local* to specify the port to bind locally (defaults to the remote port).


### stool-ssh

Ssh into the running app

#### SYNOPSIS

`stool` *global-option*... `ssh` *stage-option*... *app*

#### DESCRIPTION

Starts an ssh shell in the specified app. Reports an error if the app is not running.

The shell is refused if the current user does not have access to all fault projects of the image.

### stool-validate

Validate the stage

#### SYNOPSIS

`stool` *global-option*... `validate` *stage-option*... [`-email`] [`-repair`]

#### DESCRIPTION

Checks if the `expire` date of the stage has passes or the disk quota exceeded. If so, and if
`-repair` is specified, the stage is stopped (and also removed if expired for more than autoRemove days). And
if `-email` is specified, a notification mail is sent as configured by the notify property.

[//]: # (include stageOptions.md)

Note: This is a stage command, use `stool help stage-options` to see available [stage options](#stool-stage-options)
[//]: # (-)


## Setup

Stool is split into a client and a server part; `dashboard` as is part of the server. 
The server part is optional, it's fine to just install the client on your machine and configure it to talk to remote servers.

### Client setup

Prerequisites:
* Linux or Mac
* Java 8 or higher. This is prerequisite because Stool is implemented in Java 8, you need it to run Stool itself. 
  However, you can build and run your stages with any Java version you choose.

Install steps
* Download the latest `application.sh` file from [Maven Central](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22net.oneandone.stool%22%20AND%20a%3A%22main%22)
* Make it executable, rename it to `stool` and add it to your $PATH.
* run `stool setup`


### Server setup

Prerequisites:
* Docker 18.03 or newer

TODO


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
                 |- context          (Docker build context of the last build)
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
