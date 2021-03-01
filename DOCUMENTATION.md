# Stool

## Introduction

Stool is a tool to manage stages - create, publish, configure, delete. A stage is a Kubernetes workload, 
typically a web application.

### Quick Tour

Here's an example what you can do with Stool.

You generally invoke Stool from the command-line with `sc` followed by a command and specific arguments. 

Open a terminal and run 

    sc context
    
to see available contexts, i.e. places where you can host stages. Notes:
*  if you get an `command not found: sc` error message: Stool is not installed on your machine. 
   Please refer to the install section below. 
*  if you get an `configuration not found` error message: Stool is installed, but it's not yet
   set up (i.e. configured). Please run `sc setup`.

Choose one of the available contexts by running

    sc context <yourcontext>
    
Depending on your context you'll be asked to authenticate.

Create a new stage called `mystage` running a `hello` application with

    sc create mystage hello

You can run

    sc status mystage

to see status information about your stage. E.g. if `available` is above `0` you can point your browser 
to the url printed by the `create` or `status` command.

Use

    sc history mystage

to see the Stool commands executed for this stage.

To delete the stage run

    sc delete mystage

You can create an arbitrary number of stages. Invoke

    sc list

to see what you have created and not yet deleted. 

You can get help with

    sc help

to see a list of available commands. You can append a command to get more help on that, e.g.

    sc help create
    
prints help about `create`.

### Rationale

Technically, Stool is a Helm wrapper, a stage is a Helm releases, and stage classes provide powerful features to compute 
Helm values. Like other tools (e.g. [Helmfile](https://github.com/roboll/helmfile), Stool tries to simplify `helm`, in aims 
particularly to make value definitions more powerful, because in our use cases we have many workloads using the same Helm 
chart but with different values.

### History

Stool 5 runs stages in Docker, Stool 6 switched to Kubernetes. The general goal is to shrink Stool by replacing Stool functionality with
standard Kubernetes features/tools. Here's an outline was has already been replaced and what's planned or open:


|         | Functionality | Moved to; obsolete after |
|---------|---------|-------------|
| Stool 5 - current stable  | Process Management | Docker | 
| Stool 6 - superseded by 7 | Port Managemnt | Kubernetes |
|         | (Cpu Limits)    | Kubernetes Pod Limits |
|         | (node selection) | Kubernetes |
| Stool 7 - current development | Image Building | Maven Plugin |
|         | (K8s Resources per Stage) | Helm chart |
|         | Stage Lifecycle | Helm |
|         | Memory Limits  | Kubernetes Pod Limits |
|         | Vault Integration | configurable script |
|         | Certificate generation | configurable script |
| Stool X - thoughts only | Basic Monitoring | Prometheus? |
|         | Disk Limits | Kubernetes Ephemeral Storage Limits |
|         | Simple cli: reduce cognitive load | train developers, operating |
|         | Dashboard: None-technical UI | ? |
|         | Proxy: restricted access | All stages by Jenkins? |
|         | Stage classes | ? |


### Conventions

* Stool is written with a capital S
* `type writer font` marks things to type or technical terms from Stool.
* *italics* mark text to be replaced by the user
* bold face highlights term in definition lists
* synopsis syntax: `[]` for optional, `|` for alternatives, `...` for repeatable, `type writer` for literals, *italics* for replaceables)
* WORKSPACE denotes the workspace currently used 


## Terminology

### Stool

This term is overloaded, depending on the context it may refer to the command line client `sc`, `sc server` in Kubernetes, 
or the whole Github project. 

### Stage

A *stage* is a Kubernetes workload, typically running a web application like a Tomcat servlet container (http://tomcat.apache.org) with
a Java web application (https://en.wikipedia.org/wiki/Java_Servlet). 

Every stage has a configuration, which is a set of values you can get and set with `sc config`.

Technically, a stage is a Helm release -- `sc create` installs a Helm chart, `sc delete` uninstalls it. It's also safe to `helm uninstall`
instead of `sc delete`.

A stage runs on a Kubernetes cluster within a namespace, which is identified by a context. Every stage has a unique name in that context. 
A stage is referenced by *name*`@`*context* or just the *name* if it's in the current context. You define the stage name and context when 
you create the stage, neither can be changed later.


### Stage class

The stage class defines how to create a stage. Every stage has as class (or in oo works: it is an instance of a class).  

A class looks like this:

    name: "hello"
    extends: "kutter"
    properties:
      image: "myregistry/hello:1.0.0"
      cert: "${exec('cert.sh', stage)}"

A class has a set of properties, and it can extend other classes (i.e. inherits all properites from it). Properties can use
Freemarker templates and invoke scripts to compute values.

You usually define classes in a `stage-class.yaml` and store it in a `stage-class` label attached to an image.

Technically, the class specifies a Helm chart and how to compute its values.


### Context

A *context* specifies a place that can host stages together with the necessary authentication. There are proxy contexts and Kubernetes contexts.
A proxy context has a name, an optional token, and a URL pointing to a Stool server. A Kubernetes context is a Kubernetes context whose name is
prefixed with `kube-`. 

`sc` manages a list of proxy contexts in its configuration file. `sc` also manages a current context, 
you can change it permanently with `sc context` or per-invocation with the `-context` global option.

Advanced note: The concept of a context is similar to `kubectl`s context.


### Workspace

A workspace is a list of stages referenced by a workspace name. Use workspaces if you need to run the same commands on the a list of stages.

Add stages to workspace by passing a workspace to `sc create` or with `sc attach`. Remove stages from workspaces with `sc detach` .


### Settings

Stool is configured via settings specified in its configuration.yaml. A setting is a key/value pair. Value has a type 
(string, number, date, boolean, list (of strings), or map (string to string)). Settings are global, they apply to all stages, 
they are usually adjusted by system administrators. 


### Properties

Stages are configured via properties. A property is a key/value pair. Properties configure the respective stage only, every stage has 
its own set of properties. You can inspect and adjust properties with [stool config](#sc-config). 

Technically, properties are values of the Helm release of this stage.

Besides properties, every stage has status fields, you can view them with `sc status`. Status fields are key/values pairs like properties, 
but they are read-only.

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

Technically, a stage is a Helm release; `sc` is a wrapper for Helm that adds classes, proxying and a dashboard.


#### Commands

[//]: # (ALL_SYNOPSIS)

`sc` *global-option*... [*command* *argument*...]


`sc` [`-v`][`-e`][`-context` *context*][`-fail` *mode*] *command* *command-options*... *command-arguments*...


`sc` *global-option*... `help` [*command*]


`sc` *global-option*... `version`


`sc` *global-option*... `setup` [*spec*]


`sc` *global-option*... `context` [`-q`][`-offline`][*context*]


`sc` *global-option*... `auth` [`-batch`]


`sc` *global-option*... `create` [`-optional`][`-wait`] *name* *class* ['@'*workspace*] [*key*`=`*value*...]




`sc` *global-option*... `attach` *stage* '@'*workspace*


`sc` *global-option*... `detach` *stage* '@'*workspace*


`sc` *global-option*... `delete` *stage* [`-batch`]


`sc` *global-option*... `publish` ['-dryrun'] *stage* *class* [*key*`=`*object*...]


`sc` *global-option*... `history` *stage*


`sc` *global-option*... `config` *stage* (*key* | *key*`=`*str*)...


`sc` *global-option*... `status` *stage* (*field*|*value*)...



`sc` *global-option*... `images` *repository*



`sc` *global-option*... `list` *stage* (*field*|*property*)...


`sc` *global-option*... `port-forward` *stage* [*local-port*] *remote-port*


`sc` *global-option*... `ssh` [`-timeout` *minutes*] *stage* [*shell*]


`sc` *global-option*... `validate` [`-email`] [`-repair`] *stage*

[//]: # (-)

#### Environment

`SC_OPTS` to configure arguments `sc` passes to the underlying JVM. 
`SC_HOME` to configure Stool home directory. Defaults to `$HOME/.sc`


#### See Also

Homepage: https://github.com/mlhartme/stool

Invoke `sc help` *command* to get help for the specified command.

[//]: # (include globalOptions.md)

See `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)

### sc-global-options

Options available for all commands

#### SYNOPSIS

`sc` [`-v`][`-e`][`-context` *context*][`-fail` *mode*] *command* *command-options*... *command-arguments*...

#### DESCRIPTION

* **-v** enables verbose output
* **-e** prints stacktrace for all errors
* **-context** sets the current context for this invocation
* **-fail** see below

#### Failure mode

If you specify multiple stage for one command, you might want to specify what to do if the command
fails for some of them. That's what `-fail` *mode* is for.

Mode `normal` reports problems immediately and aborts execution, Stool does not try to run the command
on remaining matching stages. This is the default.

`after` reports problems after the command was invoked on all matching stages.

`never` is similar to `after`, but reports warnings instead of errors (and thus, Stool always returns with exit code 0).



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

Creates a fresh Stool home directory or reports an error if it already exists. The location of the home directory is configurable with 
the `SC_HOME` environment variable, it defaults to `~/.sc`. The main configuration file inside this directory is `configuration.yaml`.

Use *spec* to set up a proxy name + api url. If not specified, this is guessed from the local machine (TODO: environment file from cisotools).


### sc-context

Manage current context

#### SYNOPSIS

`sc` *global-option*... `context` [`-q`][`-offline`][*context*]

#### DESCRIPTION

When called without arguments: lists all contexts with an arrow pointing to the current one. 
Prints just the current context when called with `-q`.

Changes the current context when invoked with a *context* argument. If the new context requires authentication, this command implicitly 
runs `sc auth` to get the respective token. This can be disabled by specifying `-offline`.


### sc-auth

Authenticate to current context

#### SYNOPSIS

`sc` *global-option*... `auth` [`-batch`]

#### DESCRIPTION

Asks for username/password to authenticate against ldap. If authentication succeeds, the referenced Stool server returns an api token 
that will be stored in the client configuration file and used for future access to this context.

Use the `-batch` option to omit asking for username/password and instead pick them from the environment 
variables `STOOL_USERNAME` and `STOOL_PASSWORD`.

[//]: # (include globalOptions.md)

See `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


### sc-create

Create a new stage

#### SYNOPSIS

`sc` *global-option*... `create` [`-optional`][`-wait`] *name* *class* ['@'*workspace*] [*key*`=`*value*...]



#### DESCRIPTION

Creates a new stage: computes all properties of *class* and its base classes, except those key-value pairs specified explicitly.
The resulting values are passed to Helm to install the chart of the class.

*name* specifies the name for new stages. It must contain only lower case ascii characters or digit or dashes, it's 
rejected otherwise because it would cause problems with urls or Kubernetes objects that contain the name. 

*class* specifies the class underlying this stage. Classes can be specified in three ways: 
* a path pointing to a yaml file containing the class; this path has to start with a '/' or a '.'
* an image with a `stage-class` label containing a base64 encoded class file
* a class name defined in the configured classpath


If a *workspace* is specified, the resulting stage is added to it.

Specify `-wait` to wait for pods to become available before returning from this command.

Reports an error if a stage already exists. Or omits stages creation if the `-optional` option is specified.

[//]: # (include globalOptions.md)

See `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)

### sc-stage-argument

Stage Argument.

#### Description

Most Stool commands are stage commands, i.e. they operate on one or multiple stages. All stage commands use the same 
*stage* argument to select the stage(s) to operate on. The general form of this argument is:

`%all` operates on all stages in the current context

`@`*workspace*  operation on all stages of the respective workspace

*predicate* operates on all matching stages in the current context. The syntax for predicates is as follows:

              predicate = and {',' and}
              and = expr {'+' expr}
              expr = NAME | cmp
              cmp = (FIELD | PROPERTY) ('=' | '!=') (STR | prefix | suffix | substring)
              prefix = VALUE '*'
              suffix = '*' STR
              substring = '*' STR '*'
              NAME       # name of a stage
              FIELD      # name of a status field
              PROPERTY   # name of an class property
              STR        # arbitrary string


The most common predicate is a simple `NAME` that refers to the respective stage.

Next, a predicate *FIELD*`=`*STR* matches stages who's status field has the specified string.
*VALUE*`=`*STR* is similar, it matches stage values.

#### Examples

`sc status foo` prints the status of stage `foo`.

`sc config replicas=0 replicas=1` sets one replica for all stages that have none.

`sc delete %all -fail after` deletes all stages. Without `-fail after`, the command would abort after the first 
stage that cannot be deleted.


### sc-attach

Attach stage to a workspace

#### SYNOPSIS

`sc` *global-option*... `attach` *stage* '@'*workspace*

#### DESCRIPTION

Attaches the specified stage to *workspace*. Creates a new workspace if the specified one does not exist.


[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage](#sc-stage-argument) argument,
use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)

### sc-detach

Detach a stage from a workspace

#### SYNOPSIS

`sc` *global-option*... `detach` *stage* '@'*workspace*

#### DESCRIPTION

Removes stages from *workspace* without modifying the stage itself. Removes the workspace if it becomes empty.

[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage](#sc-stage-argument) argument,
use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


### sc-delete

Deletes a stage

#### SYNOPSIS

`sc` *global-option*... `delete` *stage* [`-batch`]

#### Description

Deletes the stage, i.e. deletes it from the respective cluster. This includes containers and log files.
If stage is specified as a workspace, it is removed from the workspace as well.

Before actually touching anything, this command asks if you really want to delete the stage. You can suppress this interaction 
with the `-batch` option.

[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage](#sc-stage-argument) argument,
use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


### sc-publish

Publish a stage

#### SYNOPSIS

`sc` *global-option*... `publish` ['-dryrun'] *stage* *class* [*key*`=`*object*...]

#### Description

Updates the stage with the specified values. *class* specifies the application to actually start, see create command for more details.

TODO: Publishing is refused if the user who built the image does not have access to all fault projects referenced by the image.

Publishing is refused if your stage has expired. In this case, publish with a new expire value.

TODO: The hostname of the container is set to <id>.<servername>, where id is a hash of stage name and application name. This hash
serves two purposes: it has a fixed length, so I'm sure the resulting name does not exceed the 64 character limit for host names. 
And the hash makes it impossible to derived stage or application name from the hostname -- applications are strongly discouraged to 
check the hostname to configure themselves, use environment variables defined for that purpose instead. Future versions of Stool will 
remove the server name from the container's hostname as well. 
TODO: how to define additional environment variables?


[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage](#sc-stage-argument) argument,
use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


### sc-history

Display commands invoked on this stage

#### SYNOPSIS

`sc` *global-option*... `history` *stage*

#### DESCRIPTION

Prints the `sc` commands that affected the stage. Invoke it `-v` to see more details for each invocation.

[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage](#sc-stage-argument) argument,
use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


### sc-config

Manage stage properties

#### SYNOPSIS

`sc` *global-option*... `config` *stage* (*key* | *key*`=`*str*)...

#### DESCRIPTION

This command gets or sets stage [properties](#properties). 

When invoked without arguments, all stage properties are printed.
When invoked with one or more *key*s, the respective properties are printed.
When invoked with one or more assignments, the respective properties are changed.

Strings may contain `{}` to refer to the previous property value. You can use this, e.g., to append to a value:
`sc config "metadataComment={} append this"`.

If you want to set a property to a String with spaces, you have to use quotes around the assignment.

If you change a property, your pods might restart to apply this change.

Properties have a type: boolean, number, date, string, or list of strings.

Boolean properties by be `true` or `false`, case sensitive.

Date properties have the form *yyyy-mm-dd*, so a valid `metadataExpire` value is - e.g. -`2016-12-31`. Alternatively, 
you can specify a number which is shorthand for that number of days from now (e.g. `1` means tomorrow).

List properties (e.g. `metadataContact`) are separated by commas, whitespace before and after an item is ignored.

[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage](#sc-stage-argument) argument,
use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


#### Available stage properties

Stool exposed all values of the underlying Helm chart as properties. This usually includes:

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

`sc` *global-option*... `status` *stage* (*field*|*value*)...


#### DESCRIPTION

Prints the specified status *field*s or class *properties*. Default: all status fields except *class*.

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
* **class**
  TODO


[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage](#sc-stage-argument) argument,
use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


### sc-images

Display images with labals

#### SYNOPSIS

`sc` *global-option*... `images` *repository*


#### DESCRIPTION

Display info about the images in the specified repository.

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
  

[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage](#sc-stage-argument) argument,
use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


### sc-list

List stages

#### SYNOPSIS

`sc` *global-option*... `list` *stage* (*field*|*property*)...

#### DESCRIPTION

Displays status of all stages (or the stages specified by `-stage`) as a table. See the `status`
command for a list of available fields. Default fields/values are `name image last-deployed`.

[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage](#sc-stage-argument) argument,
use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


### sc-port-forward

Start port forwarding

#### SYNOPSIS

`sc` *global-option*... `port-forward` *stage* [*local-port*] *remote-port*

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

`sc` *global-option*... `ssh` [`-timeout` *minutes*] *stage* [*shell*]

#### DESCRIPTION

Executes an interactive shell in the main container (i.e. the container running the web app) of this stage.
The default shell is `/bin/sh`. Reports an error if the stage is not running.

The shell is refused if the current user does not have access to all fault projects of the image.

### sc-validate

Validate the stage

#### SYNOPSIS

`sc` *global-option*... `validate` [`-email`] [`-repair`] *stage*

#### DESCRIPTION

Checks if the `metadataExpire` date of the stage has passed. If so, and if
`-repair` is specified, the stage is stopped (and also removed if expired for more than autoRemove days). And
if `-email` is specified, a notification mail is sent as configured by the `metadataContact` value.

[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage](#sc-stage-argument) argument,
use `sc help global-options` for available [global options](#sc-global-options)

[//]: # (-)


## Installing

TODO 

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
