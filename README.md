# Stool

Note: This is documentation for the current development branch,
see [Stool 5](https://github.com/mlhartme/stool/tree/stool-5.1) for the last stable branch.

## Introduction

Stool is a tool to manage stages - create, publish, configure, delete. A stage is a Kubernetes workload,
typically a web application.

Changes: https://github.com/mlhartme/stool/blob/stool-7.0/CHANGELOG.md

### Quick Tour

Here's an example what you can do with Stool.

You invoke Stool from the command-line with `sc` followed by a command and specific arguments.

Open a terminal and run

    sc context

to see available contexts, i.e. places where you can create stages. Notes:
*  if you get a `command not found: sc` error: Stool is not installed on your machine.
   Please refer to the install section below.
*  if you get a `settings not found` error: Stool is installed, but it's not set up.
   Please run `sc setup`.

Choose one of the available contexts by running

    sc context <yourcontext>

Depending on your context you'll be asked to authenticate.

Create a new stage called `tour` running a `hello` application with

    sc create tour hello

You can run

    sc status tour

to see status information like `urls` or `available` pods. If `available` is above `0`, you can point your browser
to one of the urls.

To delete the stage run

    sc delete tour

You can create any number of stages. Invoke

    sc list

to see them all.

### Rationale

Stool is technically a Helm wrapper, a stage is a Helm releases, and directions evaluate to Helm values.
Like other tools (e.g. [Helmfile](https://github.com/roboll/helmfile), Stool tries to simplify Helm. Stool puts
an emphasize on powerful value definitions because in our use cases we have many workloads using the same Helm
chart with different values.

### History

Stool 5 runs stages in Docker, Stool 6 switches to Kubernetes. The general goal is to shrink Stool by replacing Stool
functionality with standard Kubernetes features/tools. Here's an outline what has already been replaced and what's
planned or open:


|         | Functionality | Moved to; obsolete after |
|---------|---------|-------------|
| Stool 5 - current stable  | Process Management | Docker |
| Stool 6 - superseded by 7 | Port Management | Kubernetes |
|         | (Cpu limits)    | Kubernetes Pod Limits |
|         | (node selection) | Kubernetes |
| Stool 7 - current development | Image Building | any tool, e.g. Maven Plugin |
|         | (K8s Resources implementing a stage) | Helm chart |
|         | Stage lifecycle | Helm |
|         | Memory limits  | Kubernetes Pod Limits |
|         | Vault integration | configurable script |
|         | Certificate generation | configurable script |
| Stool X - thoughts only | Basic monitoring | Prometheus? |
|         | Disk Limits | Kubernetes ephemeral storage limits |
|         | Expire Stages | K8s Cronjob? |
|         | Simple cli: reduce cognitive load | train developers + operating |
|         | Dashboard: None-technical UI | ? |
|         | Proxy: restricted access | All stages by Jenkins? |
|         | Directions | Helmfile? Shell scripts? |


### Conventions

* Stool is written with a capital S
* `type writer font` marks things to type or technical terms from Stool.
* *italics* mark text to be replaced by the user
* bold face highlights term in definition lists
* synopsis syntax: `[]` for optional, `|` for alternatives, `...` for repeatable, `type writer` for literals, *italics* for replaceables)
* WORKSPACE denotes the workspace currently used


## Terminology

### Stool

This term is overloaded, depending on the context it may refer to the command line client `sc`, a Stool server running
in Kubernetes, or the whole Github project.

### Stage

A *stage* is a Kubernetes [workload](https://kubernetes.io/docs/concepts/workloads/),
typically running a web application like a [Tomcat](http://tomcat.apache.org) servlet container with
a [Java web application](https://en.wikipedia.org/wiki/Java_Servlet).

You can create, list and delete stages with the respective Stool command. Every stage has a configuration, which is a set of
variables you can get and set with `sc config`. Every stages has a status, which is a set of fields; you can check it
with `sc status`.

A stage has a context and a unique name in that context. A stage is referenced by *name*`@`*context* or just the *name*
if it's in the current context. You define the stage name and context when you create the stage, neither can be changed
later.

Technically, a stage is a Helm release -- `sc create` installs a Helm chart, `sc delete` uninstalls it, `sc publish` and `sc config`
upgrade it. Stage variables are Helm release values (or Helm release variables, as they occasionally call it).
It's safe to use `helm uninstall` instead of `sc delete`.

### Context

A *context* specifies a place that can host stages together with the necessary authentication. Stool supports Kubernetes
contexts and proxy contexts.

Use Kubernetes contexts if you have direct access to Kubernetes. `~/.kube/config` defines the available Kubernetes contexts,
each specified with a name and a cluster url.

A proxy context has a name, an optional token, and a URL pointing to a Stool server. It's to give restricted access to Kubernetes
only, and to centrally manage Stool settings. `sc` manages a list of proxy contexts in its settings.

Stool manages a current context in its settings. You can change it permanently with `sc context` or per-invocation
with the `-context` global option.


### Directions

Directions augment charts to define workloads.

Directions (or, more explicitly: the direction list) define how to create and publish stages. Stage directions are the directions
the stage was created or last published with.

Directions look something like this:

    DIRECTIONS: "hello"
    EXTENDS: "kutter"
    image: "myregistry/hello:1.0.0"
    cert:
      private: true
      expr: "script.cert(stage)"

Directions have a subject (to allow others to reference it) and can extend other direction (i.e. inherit all directions).
Each direction has a name and an expression.

Directions define the available variables of the stage, the expression is evaluated to determine the initial values.

The expression is denoted as a string. You can use [Freemarker](https://freemarker.apache.org) templating in it,
and every toolkit script is available as a Freemarker function.

Applications usually define directions in a `directions.yaml` file and attach them to images in a `directions` label.

In addition, Stool is usually configured with pre-defined directions in its toolkit.

Technically, directions specify a Helm chart and how to evaluate its values. I.e. directions supply all info needed for
Helm install/upgrade.

### Toolkit

The toolkit defines a set of charts, scripts and directions. TODO


### Variables

Stages are configured via variables, the set of variables of a stage is called its configuration.
You can inspect and adjust variables with [stool config](#sc-config).

A variable has a name, a value, and an associated direction. Stage directions define the available variables.
The value is evaluated by the associated direction expression when the stage is created, published
or configured. However, you can configure a fixed value by passing an assignment to `create`, `publish` or `config`.
In this case, the value remains unchanged until you configure a new value or remove it with *key*`-`

Variables apply to one stage only, every stage has its own set of variables, even
if the associated directions are the same.

Distinguish variables from status fields: every stage has status fields, you can view them with `sc status`.
Status fields are key/values pairs like variables, but they are read-only.

TODO: common variables: metadataExpire, metadataContact, metadataComment, replicas, ...

Technically, variable values are Helm values of the respective Helm release.


### Stage Expiring

Every stage has an `metadataExpire` variable that specifies the date until the stage is needed. You can see the expire date with `sc config metadataExpire`.
If this date has passed, the stage is called expired, and it is automatically stopped, a notification email is sent, and you cannot start it
again unless you specify a new date with `sc config metadataExpire=`*yyyy-mm-dd*.

Depending on the `autoRemove` setting, an expired stage will automatically be removed after the configured number of days.

Stage expiring helps to detect and remove unused stages, which is handy (and sometimes even crucial) if you are not the only user of a server.
If you receive an email notification that your stage has expired, please check if your stage is still needed. If so, adjust the expire date.
Otherwise, remove the stage.

### Workspace

A workspace is a list of stages referenced by a workspace name. Use workspaces if you need to run the same commands on the a list of stages,

Add stages to workspace by passing a workspace to `sc create` or with `sc attach`. Remove stages from workspaces with `sc detach` .

To use a workspace for a command, invoke it with `@` workspace instead of the stage name

Example: Suppose you have two stage, you can run status on them like this:

    sc attach one @ws
    sc attach two @ws
    sc status @ws


### Settings

Stool is configured via settings specified in its `settings.yaml` file. A setting is a key/value pair. Value has a type
(string, number, date, boolean, list (of strings), or map (string to string)). Settings are global, in contrast to variables,
they are not specific for a stage. Settings are usually adjusted by system administrators.

TODO: available settings, toolkit etc ...

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

Technically, a stage is a Helm release; `sc` is a wrapper for Helm that adds directions, proxying and a dashboard.


#### Commands

[//]: # (ALL_SYNOPSIS)

`sc` *global-option*... [*command* *argument*...]


`sc` *global-option*... `help` [*command*]


`sc` *global-option*... `version`


`sc` *global-option*... `setup` [`-proxyPrefix` *prefix*][*key*`=`*value* ...]


`sc` *global-option*... `context` [`-q`][`-offline`][*context*]


`sc` *global-option*... `auth` [`-batch`]


`sc` *global-option*... `list` [*stage*] (*field*|*variable*)...


`sc` *global-option*... `create` [`-optional`][`-wait`] *name* *directions* ['@'*workspace*] [*key*`=`*value* | *key*`-` ...]


`sc` *global-option*... `publish` ['-dryrun'] *stage* [*directions*] [*key*`=`*value* | *key*`-` ...]


`sc` *global-option*... `config` *stage* [*key* | *key*`=`*value* | *key*`-` ...]


`sc` *global-option*... `status` *stage* (*field*|*value*)...



`sc` *global-option*... `delete` *stage* [`-batch`]


`sc` *global-option*... `history` *stage*


`sc` *global-option*... `attach` *stage* '@'*workspace*


`sc` *global-option*... `detach` *stage* '@'*workspace*


`sc` *global-option*... `images` *repository*



`sc` *global-option*... `port-forward` *stage* [*local-port*] *remote-port*


`sc` *global-option*... `ssh` [`-timeout` *minutes*] *stage* [*shell*]


`sc` *global-option*... `validate` [`-email`] [`-repair`] *stage*


`sc` `server`


*stage* = `%all` | `@`*workspace* | *predicate*

[//]: # (-)

#### Global Options

Global options are:

`-v` enables verbose output

`-e` prints stacktrace for all errors

`-context` *context* sets the current context for this invocation

`-fail` *mode* see below

#### Failure mode

If you specify multiple stage for one command, you might want to specify what to do if the command
fails for some of them. That's what `-fail` *mode* is for.

Mode `normal` reports problems immediately and aborts execution, Stool does not try to run the command
on remaining matching stages. This is the default.

`after` reports problems after the command was invoked on all matching stages.

`never` is similar to `after`, but reports warnings instead of errors (and thus, Stool always returns with exit code 0).


#### Environment

`SC_OPTS` to configure arguments `sc` passes to the underlying JVM.
`SC_HOME` to configure Stool home directory. Defaults to `$HOME/.sc`


#### See Also

Homepage: https://github.com/mlhartme/stool

Invoke `sc help` *command* to get help for the specified command.


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

See `sc help` for available [global options](#sc)

[//]: # (-)


### sc-setup

Setup Stool

#### SYNOPSIS

`sc` *global-option*... `setup` [`-proxyPrefix` *prefix*][*key*`=`*value* ...]

#### DESCRIPTION

Creates a fresh Stool home directory or reports an error if it already exists. The location of the home directory is configurable with
the `SC_HOME` environment variable, it defaults to `~/.sc`. The main configuration file inside this directory is `settings.yaml`.
Stool checks for an environment variable `SC_SETUP_SETTINGS`. If it exists, the referenced file defines the initial settings (using *prefix*
to prefix all proxy names (default is ""). Otherwise, default settings are generated.

Use key/values pairs to configure local settings or proxies.

#### EXAMPLES

Create a home directory with defaults: `sc setup`
Create a home directory with a custom toolkit: `sc setup toolkit=/my/path/to/toolkit`


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

Authenticate to current proxy context

#### SYNOPSIS

`sc` *global-option*... `auth` [`-batch`]

#### DESCRIPTION

Asks for username/password to authenticate to the current context. Reports an error when used with a Kubernetes context.

Proxy contexts authenticate with username/password against ldap. If successful, the referenced Stool server returns an
api token that will be stored in the settings file and used for future access to this context.

Use the `-batch` option to omit asking for username/password and instead pick them from the environment
variables `STOOL_USERNAME` and `STOOL_PASSWORD`.

[//]: # (include globalOptions.md)

See `sc help` for available [global options](#sc)

[//]: # (-)


### sc-list

List stages

#### SYNOPSIS

`sc` *global-option*... `list` [*stage*] (*field*|*variable*)...

#### DESCRIPTION

Displays status of all stages of the current context (or the stages specified by *stage*) as a table.
See the `status` command for a list of available fields. Default fields/variables are `name origin last-deployed`.

[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage argument](#sc-stage-argument),
use `sc help` for available [global options](#sc)

[//]: # (-)


### sc-create

Create a new stage

#### SYNOPSIS

`sc` *global-option*... `create` [`-optional`][`-wait`] *name* *directions* ['@'*workspace*] [*key*`=`*value* | *key*`-` ...]

#### DESCRIPTION

Creates a new stage as defined by *directions*: evaluates all direction expressions of the stage,
except those key-value pairs specified explicitly. The resulting values define the stage configuration.
This configuration is passed to Kubernetes to setup the workload.

*name* specifies the stage name. It must contain only lower case ascii characters or digit or dashes, it's
rejected otherwise because it would cause problems with urls or Kubernetes objects that contain the name.

*directions* is a reference to the directions for this stage. Directions can be referenced in three ways:
* a path pointing to a local yaml file containing the directions; this path has to start with a `/` or a `.`
* an image with a `directions` label containing a base64 encoded directions yaml
* a directions name defined in the configured toolkit

Note that previous changes by the `config` command get lost unless you repeat them in the assignment arguments.
You'll normally call publish without arguments and thus get the configuration as defined by the directions.

Except for temporary assignments, you should adjust the directions.

If a *workspace* is specified, the resulting stage is added to it.

Specify `-wait` to wait for pods to become available before returning from this command.

Reports an error if a stage already exists. Or omits stages creation if the `-optional` option is specified.

[//]: # (include globalOptions.md)

See `sc help` for available [global options](#sc)

[//]: # (-)


### sc-publish

Publish a stage

#### SYNOPSIS

`sc` *global-option*... `publish` ['-dryrun'] *stage* [*directions*] [*key*`=`*value* | *key*`-` ...]

#### DESCRIPTION

Similar to [create](#sc-create), but updates *stage* instead of creating a new one. Fixed values (either specified
by an assignment or fixed by a previous call) remain unchanged, all other values are newly evaluates by the associated
direction. The workload is updated without downtime.

*directions* is optional - if not specified, the stage directions are used. This is useful to cause a re-start
(depends on the workload if that works).

Publish prints the variables modified by the command. Invoke with `-dryrun` to see
this diff without actually changing anything.

Publishing is refused if your stage has expired. In this case, publish with a new expire value.

TODO: Publishing is refused if the user who built the image does not have access to all fault projects referenced by the image.

[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage argument](#sc-stage-argument),
use `sc help` for available [global options](#sc)

[//]: # (-)

### sc-config

Manage stage configuration

#### SYNOPSIS

`sc` *global-option*... `config` *stage* [*key* | *key*`=`*value* | *key*`-` ...]

#### DESCRIPTION

Stage configuration is the set of its variables. This command gets or sets stage [variables](#variables).

When invoked without arguments, all variables are printed, together with the documentation available.
When invoked with one or more *key*s, the respective variables are printed.
When invoked with one or more assignments or minus, the respective variable values a fixed to the specified value or
a fixed value is removed.

A fixed variable sticks to specified value; in contrast: none-fixed variables are re-evaluated by the `publish` or `config` commands.

*str* may contain `{}` to refer to the previous value. You can use this, e.g., to append to a value:
`sc config "metadataComment={} append this"`.

If you want to set a variable to a string with spaces, you have to use quotes around the assignment.

Variables have a type: boolean, number, date, string, or list of strings.

Boolean values may be `true` or `false`, case sensitive.

Date values have the form *yyyy-mm-dd*, so a valid `metadataExpire` value is - e.g. -`2016-12-31`. Alternatively,
you can specify a number which is shorthand for that number of days from now (e.g. `1` means tomorrow).

List values (e.g. `metadataContact`) are separated by commas, whitespace before and after an item is ignored.

If you change a variable `a` that's referenced by the direction of another variable `b`, variable `b` is re-evaluate and might
change as well, even if it's not explicitly mentioned in the command line.

Technically, variables are modified by running Helm upgrade with both modified an unmodified values.


[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage argument](#sc-stage-argument),
use `sc help` for available [global options](#sc)

[//]: # (-)

#### AVAILABLE VARIABLES

The following variable are mandatory for all stages: https://github.com/mlhartme/stool/blob/stool-7.0/src/main/resources/stage.yaml

To see all variables for a given stage, along with the documentation available, run `sc config givenStage`

#### EXAMPLES

`sc config metadataComment` prints the current `comment` value.

`sc config metadataComment=42` sets the comment to 42.


### sc-status

Display stage status

#### SYNOPSIS

`sc` *global-option*... `status` *stage* (*field*|*value*)...


#### DESCRIPTION

Prints the specified status *field*s or *values*. Default: all status fields except *directions*.

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
  When this stage was last published or re-configured.
* **cpu**
  Cpu usage reported by Docker: percentage of this container's cpu utilisation relative to total system utilisation.
* **mem**
  Memory usage reported by Docker
* **images**
  Available images in repository for this stage.
* **chart**
  Helm chart the defines this stage.
* **directions**
  The directions for this stage.


[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage argument](#sc-stage-argument),
use `sc help` for available [global options](#sc)

[//]: # (-)


### sc-delete

Delete a stage

#### SYNOPSIS

`sc` *global-option*... `delete` *stage* [`-batch`]

#### DESCRIPTION

Deletes the stage, i.e. deletes it from the respective cluster. If stage is specified as a workspace,
it is removed from the workspace as well.

Before actually touching anything, this command asks if you really want to delete the stage.
You can suppress this interaction with the `-batch` option.

[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage argument](#sc-stage-argument),
use `sc help` for available [global options](#sc)

[//]: # (-)


### sc-history

Display commands invoked on this stage

#### SYNOPSIS

`sc` *global-option*... `history` *stage*

#### DESCRIPTION

Prints the `sc` commands that affected the stage. Invoke it `-v` to see more details for each invocation.

[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage argument](#sc-stage-argument),
use `sc help` for available [global options](#sc)

[//]: # (-)

### sc-attach

Attach stage to a workspace

#### SYNOPSIS

`sc` *global-option*... `attach` *stage* '@'*workspace*

#### DESCRIPTION

Attaches the specified stage to *workspace*. Creates a new workspace if the specified one does not exist.


[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage argument](#sc-stage-argument),
use `sc help` for available [global options](#sc)

[//]: # (-)

### sc-detach

Detach a stage from a workspace

#### SYNOPSIS

`sc` *global-option*... `detach` *stage* '@'*workspace*

#### DESCRIPTION

Removes stages from *workspace* without modifying the stage itself. Removes the workspace if it becomes empty.

[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage argument](#sc-stage-argument),
use `sc help` for available [global options](#sc)

[//]: # (-)



### sc-images

Display images with labels

#### SYNOPSIS

`sc` *global-option*... `images` *repository*


#### DESCRIPTION

Display info about the images in the specified repository.

TODO: this command is awkward, dump and suggest some standard-tool instead?


[//]: # (include stageArgument.md)

Note: Use `sc help stage-argument` to read about the [stage argument](#sc-stage-argument),
use `sc help` for available [global options](#sc)

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

Note: Use `sc help stage-argument` to read about the [stage argument](#sc-stage-argument),
use `sc help` for available [global options](#sc)

[//]: # (-)


### sc-server

Start server

#### SYNOPSIS

`sc` `server`

#### DESCRIPTION

Starts a Stool server, which is a web application implementing the proxy and the dashboard.
The server runs in the current console until it's stopped with ctrl-c.


### sc-stage-argument

Stage argument

#### SYNOPSIS

*stage* = `%all` | `@`*workspace* | *predicate*

#### DESCRIPTION

Most Stool commands take a *stage* argument to specify the stages to operate on. The general form of *stage* is:

`%all` specifies all stages in the current context

`@`*workspace*  specifies all stages in the respective workspace

*predicate* specifies all matching stages in the current context. The syntax for predicates is as follows:

              predicate = and {',' and}
              and = expr {'+' expr}
              expr = NAME | cmp
              cmp = (FIELD | VARIABLE) ('=' | '!=') (STR | prefix | suffix | substring)
              prefix = VALUE '*'
              suffix = '*' STR
              substring = '*' STR '*'
              NAME       # name of a stage
              FIELD      # name of a status field
              VARIABLE   # name of a variable
              STR        # arbitrary string


The most common predicate is a simple `NAME` that refers to the respective stage in the current context.

Next, a predicate *FIELD*`=`*STR* matches stages who's status field has the specified string.
*VALUE*`=`*STR* is similar, it matches stage values.

#### EXAMPLES

`sc status foo` prints the status of stage `foo`.

`sc config replicas=0 replicas=1` sets one replica for all stages that have none.

`sc delete %all -fail after` deletes all stages. Without `-fail after`, the command would abort after the first
stage that cannot be deleted.


## Installing

Prerequisites:
* Linux or Mac
* Java 16 or higher.
* If you want to use Kubernetes Contexts: [Helm](https://helm.sh) 3.

Install steps
* Download the latest `application.sh` file from [Maven Central](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22net.oneandone.stool%22%20AND%20a%3A%2stool%22)
* Make it executable, rename it to `sc` and add it to your $PATH.
* run `sc setup`
* adjust `~/.sc/settings.yaml`


### Development

To build Stool, you need:
* Linux or Mac
* Java 15+
* Maven 3+
* Helm 3
* Git
* Docker (with api 1.26+) and Kubernetes.
* TODO: depends on fault and cisotools

#### Package structure

The rough layering of packages is

    cli, server
    core
    directions
    registry, kubernetes
    util

Upper level user lower level, but not neighbours.


#### Docker setup

Docker on macOS should be fine out of the box (it sets up a Linux VM, and all docker containers run as the development user).

With Linux, you either setup Docker to run as your user or as a root user.

##### Linux - Containers run as root

This is the normal setup: install Docker for you Linux distribution and make sure that the development user has permissions to access
the docker socket (usually be adding him/her to the Docker group).

In addition, you need to make sure that root has access to the Fault workspace:

* add `user_allow_other` to `/etc/fuse.conf`
* append `-Dfault.mount_opts=allow_root` to `FAULT_OPTS`

TODO: that's all to make it work?


##### Linux - Containers run as development user

Running Containers as a none-root user is more secure and saves some trouble when Stool Server needs access to the Fault Workspace.
To configure this you have to a) execute containers as the development user, and b) adjust docker socket permissions that
are accessible from containers (which is needed for Stool server).

* edit `/lib/systemd/system/docker.socket` and change `group` entry to the primary group of your development user
  (note that this can't be done in `/etc/docker/daemon.json` because systemd sets up the socket)
* setup user ns mapping for your Docker daemon to run all containers as your development user.
  This is a common security measure and, it makes sure that files in bind mounted directories are creates with normal development
  user and group. To do so:
  (replace <user> with your developer user (e.g. mlhartme), <group> with its primary group (usually the same as <user>,
  <uid> with your developer user id (e.g. 1000), and gid with your development user's primary group id (e.g. 1000);
  run `id` to see your values)
  * /etc/docker/daemon.json

        {
          "userns-remap": "<user>:<group>"
        }

  * /etc/subuid

        <user>:<uid>:1
        <user>:100000:65535

  * /etc/subgid

        <group>:<gid>:1
        <group>:100000:65535


Apply changes with

    systemctl daemon-reload
    systemctl restart docker

Check if `/var/run/docker.sock` is created with the correct primary group. Also check
`journalctl -u docker` for errors. Note that the following warnings seem to be ok for Stool (i've seen them on Debian and Majaro):

    Your kernal does not support cgroup rt period
    Your kernel does not support cgroup rt runtime

Next, run `docker version`, you should get both the server and the client version. If Docker reports a permission problem, check
the above `group` configuration for `/var/run/docker.sock`.

Finally, make sure you have a `stool` network, it's needed for tests and to run the server:

    docker network create stool


#### Secrets in .fault/net.oneandone.stool:stool

* tomcat.p12

  puppet.exec("openssl", "pkcs12", "-export", "-in", chain.getAbsolute(),
  "-inkey", key.getAbsolute(),
  "-out", p12.getAbsolute(), "-name", "tomcat", "-passout", "pass:changeit");

* generate fault key:

  ssh-keygen -t rsa -b 4096 -N "" -m pem -C "stool@test.pearl.server.lan" -f ./test-pearl.key

* add hostkey to fault

  fault host-add -fqdn=public_test_pearl -public-key test-pearl.key.pub


#### Building

Get Stool sources with

    git clone https://github.com/mlhartme/stool.git

Stool has pretty standard Maven build, run

    cd stool
    mvn clean install

The main build result is `target/sc`. Add it to your path and make sure that

    sc -v version

print the correct build date.

#### Integration tests

To enable the integration tests: touch `it.properties` and add the following entries
  * `kubernetes`: context to use for integration tests
  * `toolkit`: reference to your toolkit
  * `portus`: Portus registry with credentials to use

Note: you might want to store this file in a different location an create a symlink


#### Notes

Maven releases go to Sonatype, you need the respective account. After running `mvn release:prepare` and `mvn release:perform`,
go to the staging repository and promote the release.

