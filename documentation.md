# Stool

## Introduction

Stool is a tool to manage stages: create, build, start, stop, delete. A stage is a Web application packaged a container running in Kubernetes.
Technically, Stool is a simple Helm frontend that simplifies value handling.

### Quick Tour

Here's an example what you can do with Stool. 

You generally invoke Stool from the command-line with `sc` followed by a Stool command and arguments for the respective command. 

Open a terminal and run 

    sc context
    
to see available contexts, i.e. places where you can host stages. Notes:
*  if you get an `command not found: sc` error message: Stool is not installed on your machine. 
   Please refer to the install section below. 
*  if you get an `client configuration not found` error message: Stool is properly installed on your machine,
   but it's not yet set-up (i.e. configured). Please run `sc setup` and follow the instructions.

Choose one of the available contexts by running

    sc context <yourcontext>
    
Depending on your context you'll be asked to authenticate.

Enter a source directory with a readily built Web application of yours (i.e. `cd` into the directory) - or get a sample application with

    git clone ssh://git@github.com/mlhartme/hellowar.git
    cd hellowar
    mvn clean package
    
Create a new stage with

    sc create hello

and build an image with

    sc build   TODO: that's changed ...
    
Start it:

    sc start

To see the running application, point your browser to the url printed by the `start` command.

You can run

    sc status

to see if your stage is running and to see the stage urls.

To delete the stage, stop it with

    sc stop

and wipe it with

    sc delete

You can create an arbitrary number of stages. Invoke

    sc list

to see what you have created and not yet deleted. 

Use 

    sc history
    
to see the Stool commands executed for the current stage.

You can get help with

    sc help

to see a list of available commands. You can append a command to get more help on that, e.g.

    sc help create
    
prints help about `create`.


### Conventions

* Stool is written with a capital S
* `type writer font` marks things to type or technical terms from Stool.
* *italics* mark text to be replaced by the user
* bold face highlights term in definition lists
* synopsis syntax: `[]` for optional, `|` for alternatives, `...` for repeatable, `type writer` for literals, *italics* for replaceables)
* WORKSPACE denotes the workspace currently used 


## Terminology

### Stool

This term is overloaded, depending on the context it may refer to the command line client `sc`, the server application running in Kubernetes, 
or the whole Github project. 


### Context

A *context* specifies a place that can host stages together with the necessary authentication. It has a name, an optional token, and 
a URL pointing to a Stool server. `sc` manages a list of contexts in its client configuration file. `sc` also manages a current context, 
you can change it permanently with `sc context` or per-invocation with the `-context` global option.

Advanced note: The concept of a context is similar to `kubectl`s context.


### Stage

A *stage* is a running web application, for example a Tomcat servlet container (http://tomcat.apache.org) with a Java web application
(https://en.wikipedia.org/wiki/Java_Servlet). This application is packaged into a Docker image and installed as a Helm chart in the 
current context. Stage configuration is the set of Helm values use for this release. 

Technically, a stage is a Helm release -- `sc create` installs a Helm chart, `sc delete` uninstalls it. It's also safe to `helm uninstall`
instead of `sc delete`.

A stage is hosted in a Kubernetes namespace, which is identified by a context. Every stage has a unique name in that context. A stage is 
referenced by *name*`@`*context* or just the *name* if it's in the current context. The stages attached to a workspace are shown in your 
shell prompt. The stage name is part of the application url(s). You define the stage name and context when you create the stage, neither
can be changed later.


### Image

A Docker image with various labels. An image is uniquely identified by a repository tag. However, since a stage implies the repository, 
Stool usually refers to an image by its tag. When building images with Stool, this tag is a number, that's incremented with every build.

Image labels configure how to handle this image with Stool. Available labels:0

Labels: TODO

* **origin**
  Specifies where the image came from, e.g. a git url, Maven like
      git:ssh://git@github.com/mlhartme/hellowar.git


### Workspace

A workspace is a list of stages associated with the current directory tree.

You'll typically work with workspaces like this: you have a checkout of the sources of one or multiple applications of yours. If the source 
code is Java, you build and deploy the image with something like `mvn clean deploy`. Not that you generally have to deploy it because the 
a local Docker image is not available on a deparate Kubernetes cluster. To get a stage with that image, run `sc create`, which also creates 
a workspace with the newly created stages. You work with your stage (e.g. build it with `sc config` or `sc publish`), and when you're done, 
you clean up with `sc delete`. You can also use `sc attach` to work with existing stages, and `sc detach` to remove stages from a workspace 
without deleting them.

Technically, the workspace is stored in `.backstage/workspace.yaml`

The current workspace used by a Stool command is determined by searching the working directory and it parents for a workspace file. 

Instead of using workspaces, you can also use `sc` with an explict `-stage <name>` command instead.


### Current stages and stage indicator

The *current stages* are the stages referenced by the current workspace. Unless otherwise specified, stage commands operate on the current 
stages.

The stage indicator `> somestage@context <` is displayed in front of your shell prompt, it lists the current stages. The context is omitted 
if it's the current context.

If you create a new stage, Stool creates a new workspace and attaches it to the newly created stage(s). If you `cd` into a different 
workspace, the stage indicator changes accordingly. You can explicitly change the attached stage with `sc attach` and `sc detach`. The 
stage indicator is invisible if you have no current workspace.


### Settings

Stool server is configured via settings. A setting is a key/value pair. Value has a type (string, number, date, boolean, list (of strings), 
or map (string to string)). Settings are global, they apply to all stages, they are usually adjusted by system administrators. 

### Values

Stages are configured via values. A value is a key/object pair; object has a type (string, number, date, boolean, list (of strings), 
or map (string to string)). Values configure the respective stage only, every stage has its own set of values. You can inspect and adjust 
values with [stool config](#sc-config). 

Technically, values are values of the Helm deployment represented by this stage.

Besides values, every stage has status fields, you can view them with `sc status`. Status fields are similar to values, but they are read-only.

### Stage Expiring

Every stage has an `metadataExpire` value that specifies the date until the stage is needed. You can see the expire date with `sc config metadataExpire`. 
If this date has passed, the stage is called expired, and it is automatically stopped, a notification email is sent, and you cannot start it 
again unless you specify a new date with `sc config metadataExpire=`*yyyy-mm-dd*.

Depending on the `autoRemove` setting, an expired stage will automatically be removed after the configured number of days. 

Stage expiring helps to detect and remove unused stages, which is handy (and sometimes even crucial) if you are not the only user of a server. 
If you receive an email notification that your stage has expired, please check if your stage is still needed. If so, adjust the expire date. 
Otherwise, remove the stage.

### Dashboard

The dashboard is the UI that's part of Stool server. 

## Commands

### sc

Stage control

#### SYNOPSIS

`sc` *global-option*... [*command* *argument*...]

#### DESCRIPTION

`sc` is a command line tool to manage stages. A stage is a Kubernetes workload, typically a web application.
*command* defaults to `help`. `sc` stands for stage control. 
Technically, a stage  is a Helm release, and `sc` is a wrapper for Helm with powerful value definitions
and some addional features like proxying and a dashboard.


#### Commands

[//]: # (ALL_SYNOPSIS)

`sc` *global-option*... [*command* *argument*...]


`sc` [`-v`][`-e`][`-context` *context*] *command* *command-options*... *command-arguments*...


`sc` *global-option*... `help` [*command*]


`sc` *global-option*... `version`


`sc` *global-option*... `setup` [*spec*]


`sc` *global-option*... `context` [`-q`][`-offline`][*context*]


`sc` *global-option*... `auth` [`-batch`]


`sc` *global-option*... `create` [`-optional`][`-wait`] *name* *class* ['@' *workspace*] [*key*`=`*object*...]




`sc` *global-option*... `attach` *name* '@' *workspace'


`sc` *global-option*... *stage-command* [`-all`|`-stage` *predicate*] [`-fail` *mode*] *command-options*...



`sc` *global-option*... `detach` *stage-option*... 


`sc` *global-option*... `delete` *stage-option*... [`-batch`]


`sc` *global-option*... `publish` *stage-option*... ['-dryrun'] *class* [*key*`=`*object*...]


`sc` *global-option*... `history` *stage-option*... [`-details`] [`-max` *max*] 


`sc` *global-option*... `config` *stage-option*... (*key* | *key*`=`*str*)...


`sc` *global-option*... `status *stage-option*... (*field*|*value*)...



`sc` *global-option*... `images` *stage-option*...



`sc` *global-option*... `list` *stage-option*... (*field*|*value*)...


`sc` *global-option*... `port-forward` *stage-option*... [*local-port*] *remote-port*


`sc` *global-option*... `ssh` [`-timeout` *minutes*] *stage-option*... *shell*


`sc` *global-option*... `validate` *stage-option*... [`-email`] [`-repair`]

[//]: # (-)

#### Environment

`SC_OPTS` to configure arguments `sc` passes to the underlying JVM. 
`SC_HOME` to configure Stool configuration directory. Defaults to `$HOME/.sc`


#### See Also

Homepage: https://github.com/mlhartme/stool

Invoke `sc help` *command* to get help for the specified command.

[//]: # (include globalOptions.md)

See `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)

### sc-global-options

Options available for all commands

#### SYNOPSIS

`sc` [`-v`][`-e`][`-context` *context*] *command* *command-options*... *command-arguments*...

#### DESCRIPTION

* **-v** enables verbose output
* **-e** prints stacktrace for all errors
* **-context** sets the current context for this invocation


## General commands

### sc-help 

Display man page

#### SYNOPSIS

`sc` *global-option*... `help` [*command*]

#### DESCRIPTION

Display help about the specified *command*. Or, if *command* is not specified, display general `sc` help.


### sc-version 

Display version info

#### SYNOPSIS

`sc` *global-option*... `version`

#### DESCRIPTION

Prints `sc`'s version info and, if the current context is a proxy context, the server version.

[//]: # (include globalOptions.md)

See `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


### sc-setup

Setup Stool

#### SYNOPSIS

`sc` *global-option*... `setup` [*spec*]

#### DESCRIPTION

Creates a fresh configuration directory or reports an error if it already exists.
The location of the configuration directory is configurable with the `SC_HOME` environment
variable, it defaults to `~/.sc`. The  main  configuration  file inside this  directory is
`configuration.yaml`.

Use *spec* to set up a context name + api url. If not specified, this is guessed from the local machine (TODO: cisotools).


### sc-context

Manage current context

#### SYNOPSIS

`sc` *global-option*... `context` [`-q`][`-offline`][*context*]

#### DESCRIPTION

When called without argument: lists all contexts with an arrow pointing to the current one. 
Prints just the current context when called with `-q`.

Changes the current context when invoked with a *context* argument. If the new context requires authentication, this command implicitly 
runs `sc auth` to get the respective token. This can be disabled by specifying `-offline`.


### sc-auth

Authenticate to current context

#### SYNOPSIS

`sc` *global-option*... `auth` [`-batch`]

#### DESCRIPTION

Asks for username/password to authenticate against ldap. If authentication succeeds, the referenced Stool server returns an api token 
that will be stored in the client configuration file and used for future access to this context/token.

Use the `-batch` option to omit asking for username/password and instead pick them from the environment 
variables `STOOL_USERNAME` and `STOOL_PASSWORD`.

[//]: # (include globalOptions.md)

See `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


### sc-create

Create a new stage

#### SYNOPSIS

`sc` *global-option*... `create` [`-optional`][`-wait`] *name* *class* ['@' *workspace*] [*key*`=`*object*...]



#### DESCRIPTION

Creates a new stage: computes all fields of *class* and its base classes, except those key-values pair specified explicitly.
The resulting values are passed to Helm to install the chart of the class.

*name* specifies the name for new stages. It must contain only lower case ascii characters or digit or dashes, it's 
rejected otherwise because it would cause problems with urls or Kubernetes objects that contain the name. 

If a *workspace* is specified, the resulting stage is added to it.

Specify `-wait` to wait for pods to become available before returning from this command.

Reports an error if a stage already exists. Or omits stages creation if the `-optional` option is specified.

[//]: # (include globalOptions.md)

See `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)

#### Examples

Create one stage `foo` as defined by class `hello`: `sc create foo hello`



## Stage Commands

Most Stool commands are stage commands, i.e. they operate on one or multiple stages. Typical stage commands are `status`, `publish`, 
and `delete`. All stage commands support the same stage options, see `sc help stage-options` for documentation.

### sc-stage-options

Options available for all stage commands

#### SYNOPSIS

`sc` *global-option*... *stage-command* (*predicate* | '%all' | '@' *workspace*) [`-fail` *mode*] *command-options*...


#### Stage selection

Stage commands operate on the stage(s) selected by the first argument:

`%all` operates on all stages in the current context

*predicate* operates on all matching stages in the current context. The syntax for predicates is as follows:

              or = and {',' and}
              and = expr {'+' expr}
              expr = NAME | cmp
              cmp = (FIELD | VALUE) ('=' | '!=') (STR | prefix | suffix | substring)
              prefix = VALUE '*'
              suffix = '*' STR
              substring = '*' STR '*'
              NAME       # name of a stage
              FIELD      # name of a status field
              VALUE      # name of a configuration value
              STR        # arbitrary string


The most basic predicate is a simple `NAME`. It performs a substring match on the stage name. This is handy to run one command for a stage 
without attaching it.

Next, a predicate *FIELD*`=`*STR* matches stages who's status field has the specified string.
*VALUE*`=`*STR* is similar, it matches stage values.

#### Failure mode

Since stage commands operate on an arbitrary number of stages, you might want to specify what to do if the command
fails for some of them. That's what `-fail` *mode* is for.

Mode `normal` reports problems immediately and aborts execution, Stool does not try to run the command 
on remaining matching stages. This is the default.

`after` reports problems after the command was invoked on all matching stages.

`never` is similar to `after`, but reports warnings instead of errors (and thus, Stool always returns with exit code 0).

#### Examples

`sc status -stage foo` prints the status of stage `foo`.

`sc start -all -fail after` starts all stages. Without `-fail after`, the command would abort
after the first stage that cannot be started (e.g. because it's already running).

`sc stop -stage up=true` stops all stages currently up, but aborts immediately if one stage fails to stop.


### sc-attach

Attach stage to a workspace

#### SYNOPSIS

`sc` *global-option*... `attach` *stage-option*... *stage* '@' *workspace'

#### DESCRIPTION

Attaches the specified stage to *workspace*. Creates a new workspace if the specified one does not exist.


[//]: # (include stageOptions.md)

Note: This is a stage command, use `sc help stage-options` to see available [stage options](#sc-stage-options)
Use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)

### sc-detach

Detach a stage from a workspace

#### SYNOPSIS

`sc` *global-option*... `detach` *stage-option*... *stage* '@' *workspace'

#### DESCRIPTION

Removes stages from *workspace* without modifying the stage itself. Removes the workspace if it becomes empty.

[//]: # (include stageOptions.md)

Note: This is a stage command, use `sc help stage-options` to see available [stage options](#sc-stage-options)
Use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


### sc-delete

Deletes a stage

#### SYNOPSIS

`sc` *global-option*... `delete` *stage-option*... [`-batch`]

#### Description

Deletes the stage, i.e. deletes it from the respective cluster. This includes containers and log files.
If the current workspace is attached to this stage, this attachment is removed as well.

Before actually touching anything, this command asks if you really want to delete the stage. You can suppress this interaction 
with the `-batch` option.

[//]: # (include stageOptions.md)

Note: This is a stage command, use `sc help stage-options` to see available [stage options](#sc-stage-options)
Use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


### sc-publish

Publish a stage

#### SYNOPSIS

`sc` *global-option*... `publish` *stage-option*... ['-dryrun'] *class* [*key*`=`*object*...]

#### Description

Updates the stage with the specified values. *class* specifies the class to actually start

Publishing is refused if the user who built the image does not have access to all fault projects referenced by the image.

Publishing is refused if your stage has expired. In this case, publish with a new expire value.

TODO: The hostname of the container is set to <id>.<servername>, where id is a hash of stage name and application name. This hash
serves two purposes: it has a fixed length, so I'm sure the resulting name does not exceed the 64 character limit for host names. 
And the hash makes it impossible to derived stage or application name from the hostname -- applications are strongly discouraged to 
check the hostname to configure themselves, use environment variables defined for that purpose instead. Future versions of Stool will 
remove the server name from the container's hostname as well. 
TODO: how to define additional environment variables?


[//]: # (include stageOptions.md)

Note: This is a stage command, use `sc help stage-options` to see available [stage options](#sc-stage-options)
Use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


### sc-history

Display commands invoked on this stage

#### SYNOPSIS

`sc` *global-option*... `history` *stage-option*... [`-details`] [`-max` *max*] 

#### DESCRIPTION

Prints the `sc` commands that affected the stage. Specify `-details` to also print command output. Stops after the 
specified *max* number of commands (defauls is 50).

[//]: # (include stageOptions.md)

Note: This is a stage command, use `sc help stage-options` to see available [stage options](#sc-stage-options)
Use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


### sc-config

Manage stage values

#### SYNOPSIS

`sc` *global-option*... `config` *stage-option*... (*key* | *key*`=`*str*)...

#### DESCRIPTION

This command gets or sets stage [values](#values). 

Caution: `config` does not deal Stool settings, see `sc help` for that.

When invoked without arguments, all stage values are printed.
When invoked with one or more *key*s, the respective values are printed.
When invoked with one or more assignments, the respective values are changed.

Strings may contain `{}` to refer to the previous value. You can use this, e.g., to append to a value:
`sc config "metadataComment={} append this"`.

If you want to set a value to a String with spaces, you have to use quotes around the assignment.

If you change a value, your application might be restart to apply this change.

Values have a type: boolean, number, date, string, or list of strings.

Boolean values by be `true` or `false`, case sensitive.

Date values have the form *yyyy-mm-dd*, so a valid `metadataExpire` value is - e.g. -`2016-12-31`. Alternatively, 
you can specify a number which is shorthand for that number of days from now (e.g. `1` means tomorrow).

List values (e.g. `metadataContact`) are separated by commas, whitespace before and after an item is ignored.

[//]: # (include stageOptions.md)

Note: This is a stage command, use `sc help stage-options` to see available [stage options](#sc-stage-options)
Use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


#### Available stage values

Stool exposed all values of the underlying Helm chart. In addition, every stage has the following values:

* **metadataComment**
  Arbitrary comment for this stage. This value nothing but stored, it has no effect. Type string.
* **metadataExpire**
  Defines when this stage [expires](#stage-expiring). Type date.
* **metadataContact**
  List of email addresses or `@first` (first person touching this stage) or `@last` (last person touching this stage)
  to send notifications about this stage. Type list. Default value: `@first`.


#### Examples

`sc config metadataComment` prints the current `comment` value.

`sc config metadataComment=42` sets the comment to 42.


### sc-status

Display stage status

#### SYNOPSIS

`sc` *global-option*... `status *stage-option*... (*field*|*value*)...


#### DESCRIPTION

Prints the specified status *field*s or *value*s. Default: print all fields.

Available fields:

* **name**
  Name of the stage.
* **available**
  Number of available replicas.
* **urls**
  Urls for this stage. Point your browser to one of them to access your stage.
* **first-deployed**
  When this stage was created.
* **last-deployed**
  When this stage was last updated.
* **cpu**
  Cpu usage reported by Docker: percentage of this container's cpu utilisation relative to total system utilisation.
* **mem**
  Memory usage reported by Docker
* **images**
  Available images in repository for this stage.
* **urls**
  Urls to invoke this stage.
* **origin-scm**
  SCM url label of the current image.


[//]: # (include stageOptions.md)

Note: This is a stage command, use `sc help stage-options` to see available [stage options](#sc-stage-options)
Use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


### sc-images

Display image status

#### SYNOPSIS

`sc` *global-option*... `images` *stage-option*...


#### DESCRIPTION

Display info about the images of the stage.

TODO
* **disk**
  Read/write disk space that has to be reserved for this image. Type number (mb).
* **memory**
  Memory that has to be reserved for this image. Type number (mb).
* **build args*
  Docker build arguments actually used to build this image. 
* **secrets*
  The fault projects nneded to run this stage.
* **comment**
  comment attached to the image
* **created-at**
  When this image was added to the stage.
* **created-by**
  The user who added this image to the stage.
* **origin-scm**
  Source scm this image was built from. Type string.
* **origin-user**
  Who build this image. Type string.
  

[//]: # (include stageOptions.md)

Note: This is a stage command, use `sc help stage-options` to see available [stage options](#sc-stage-options)
Use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


### sc-list

List stages

#### SYNOPSIS

`sc` *global-option*... `list` *stage-option*... (*field*|*value*)...

#### DESCRIPTION

Displays status of all stages (or the stages specified by `-stage`) as a table. See the `status`
command for a list of available fields. Default fields/values are `name image last-deployed`.

[//]: # (include stageOptions.md)

Note: This is a stage command, use `sc help stage-options` to see available [stage options](#sc-stage-options)
Use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


### sc-port-forward

Start port forwarding

#### SYNOPSIS

`sc` *global-option*... `port-forward` *stage-option*... [*local-port*] *remote-port*

#### DESCRIPTION

Starts port-forwarding of port *local-port* on localhost (default: remote port) to *remote-port* on the currently
running port of the stage. Reports an error, if the stage is not running. 
Forwarding is refused if the current user does not have access to all fault projects of the image.
Forwarding is terminated manually by pressing ctrl-c or automatically after *timeout* minutes (default: 30).

Examples: debug your application: `ssh port-forwarding 5005` and start your debugging session on localhost:5050 

Examples: attach to a JMX console via jmxmp: `ssh port-forwarding 5555` and 
`jconsole -J-Djava.class.path=${CISOTOOLS_HOME}/stool/opendmk_jmxremote_optional_jar-1.0-b01-ea.jar service:jmx:jmxmp://localhost:5555`
(Note: if the connections crashes and jconsole asks to reconnect: make sure your jconsole java version matches the application's Java version;
and make sure the jar file is referenced properly)

### sc-ssh

Ssh into the running stage

#### SYNOPSIS

`sc` *global-option*... `ssh` [`-timeout` *minutes*] *stage-option*... *shell*

#### DESCRIPTION

Executes an interactive shell in the main container (i.e. the container running the web app) of this stage.
The default shell is `/bin/sh`. Reports an error if the stage is not running.

The shell is refused if the current user does not have access to all fault projects of the image.

### sc-validate

Validate the stage

#### SYNOPSIS

`sc` *global-option*... `validate` *stage-option*... [`-email`] [`-repair`]

#### DESCRIPTION

Checks if the `metadataExpire` date of the stage has passed. If so, and if
`-repair` is specified, the stage is stopped (and also removed if expired for more than autoRemove days). And
if `-email` is specified, a notification mail is sent as configured by the `metadataContact` value.

[//]: # (include stageOptions.md)

Note: This is a stage command, use `sc help stage-options` to see available [stage options](#sc-stage-options)
Use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


## Installing

Stool is split into a client and a server part; `dashboard` as is part of the server. You'll normally install just the client part, and
the server uses a server set up by your operating team.

### Client installation

Prerequisites:
* Linux or Mac
* Java 8 or higher. This is prerequisite because Stool is implemented in Java 8, you need it to run Stool itself. 
  However, you can build and run your stages with any Java version you choose.
* Docker 1.26 or newer (used by `sc build`)

Install steps
* Download the latest `application.sh` file from [Maven Central](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22net.oneandone.stool%22%20AND%20a%3A%22main%22)
* Make it executable, rename it to `sc` and add it to your $PATH.
* run `sc setup` and follow the instructions


### Server installation

TODO 
* see https://github.com/mlhartme/stool/blob/stool-6.x/server/src/helm/values.yaml for available values
* helm install ...

Technically, Stool server is a proxy for Kubernetes, it uses a services account to access Kubernetes API. Users authenticate against Stool
server, they do not have access to Kubernetes.

### Building Stool

See https://github.com/mlhartme/stool/blob/stool-6.x/DEVELOPMENT.md
