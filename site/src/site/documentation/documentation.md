# Stool

Stage Tool
Author: Michael Hartmeier
Copyright 2016 1&amp;1 Internet AG

Stool <?eval ${project.version}?>,

<xi:include href="../../../target/now.txt" parse="text"/>


  <!-- Conventions
       * 'Stool' is written with a capital S
       * backquotes mark things to type or technical term from Stool. 
       * <replaceable/> is used to mark user input
    -->


## Introduction

Stool is a command line tool that provides a lifecycle for stages: create, build, start, stop, remove.
A stage is a Tomcat with web applications built from sources or downloaded as artifacts.


### Quick Tour

Here's an example, what you can do with Stool. (The following assumes that Stool has been installed properly - see the setup section below)

Create a new stage by checking out an application:

        <programlisting>stool create git:ssh://git@github.com/mlhartme/hellowar.git</programlisting>

Build the application:

        <programlisting>stool build</programlisting>

Start it:

        <programlisting>stool start</programlisting>

To see the running application, point your browser to the url printed by the `start` command.

You can invoke

        <programlisting>stool status</programlisting>

to see if your application is running and to see the application urls.

To remove the stage, stop the application with

        <programlisting>stool stop</programlisting>

and dump it from your disk with

        <programlisting>stool remove</programlisting>

You can create an arbitrary number of stages. Invoke

        <programlisting>stool list</programlisting>

to see what you have created and not yet removed. To switch to another stage, invoke

        <programlisting>stool select otherstage</programlisting>

You can get help with

        <programlisting>stool help</programlisting>

to see a list of available commands. You can append a command to get more help on that, e.g.
<programlisting>stool help create</programlisting> prints help about `create`.

### Rationale

Why not setup Tomcat by hand or via the admin application? Because Stool makes it simpler, more robust,
it deals with port allocation, different users sharing their stages, etc.

Why not use virtual machines instead of creating stages with Stool? Stool offers the following benefits:

* creating stages is faster
* creating stages is fully automatic, virtual machine might need extra steps like dns setup, cerfificates etc.
* if you have firewalls, you can set them up once for all stages; with virtual machine, you'll probably have to
  request firewalls for every new vm.

Thus, a stage is not a micro service - all stages shared the same container and os.

## Concepts

### Stage

A stage is a Tomcat servlet container (http://tomcat.apache.org) with one or more Java web applications
(https://en.wikipedia.org/wiki/Java_Servlet). The whole thing is wrapper by Java Service Wrapper
(http://wrapper.tanukisoftware.com/doc/german/download.jsp) for robust start/stop handling.
A stage has a

        <itemizedlist>
          <listitem>
            <formalpara>
              <title>directory</title>
Where the stage is stored in your file system, it holds the source code or the war files of this stage.
This is where you usually work with your stage. The directory is determined when you create a
stage. You can change the stage directory with `stool move`.

            </formalpara>
          </listitem>
          <listitem>
            <formalpara>
              <title>id</title>
Unique identifier for a stage. The id is generated when creating a stage and it is never changed.
However, users normally work with the stage name instead.

            </formalpara>
          </listitem>
          <listitem>
            <formalpara>
              <title>name</title>
User readable indentification for a stage. Usually, the name is unique. The name of the selected
stage is shown in your shell prompt, you use it to switch between stages, and it's part of the application
url(s). The name is determined when you create a stage (in most cases it's simply the name of the stage
directory). You can change the name with `stool config name=`<replaceable>renamed</replaceable>.
            </formalpara>
          </listitem>
          <listitem>
            <formalpara>
              <title>url</title>
Specifies where the web applications come from: A Subversion URL, a git url, Maven coordinates, or
a file url pointing to a war file.
<programlisting>Examples:
  git:ssh://git@github.com/mlhartme/hellowar.git
  svn:https://github.com/mlhartme/hellowar/trunk
  gav:net.oneandone:hellowar:1.0.2
  file:///home/mhm/foo.war
</programlisting>
            </formalpara>
          </listitem>
          <listitem>
            <formalpara>
              <title>type</title>
How the stage contains the application(s): source - checkout of a Maven project, or artifact - a Maven artifact.
The stage url implies the stage type.
            </formalpara>
          </listitem>
          <listitem>
            <formalpara>
              <title>state</title>
one of
                <variablelist>
                  <varlistentry>
                    <term>down</term>
                    <listitem>
stage is not running, applications cannot be accessed. This is the initial state after creation or after
it was stopped.
                    </listitem>
                  </varlistentry>
                  <varlistentry>
                    <term>up</term>
                    <listitem>
stage is running, applications can be access via application url(s). This is the state after successful
start or restart.
                    </listitem>
                  </varlistentry>
                  <varlistentry>
                    <term>sleeping</term>
                    <listitem>
stage is temporarily not running; state after stage was stopped with `-sleep`.
This state is used e.g. when a machine is rebooted, it flags the stages that should be started once the
machine is up again.
                    </listitem>
                  </varlistentry>
                </variablelist>

            </formalpara>
You can check the state with `stool status` or `stool list`.
          </listitem>
          <listitem>
            <formalpara>
              <title>owner</title>
see below
            </formalpara>
          </listitem>
        </itemizedlist>

Note: A system stage is a stage whose directory is placed in $STOOL_HOME/system. System stages get special treatment in
system-start and system stop and they are not listed by the Dashboard.
    </sect1>

### Selected stage and stage indicator

The selected stage is the stage the current working directory belongs to. In other words: your current working directory is the
stage directory or a direct or indirect subdirectory of it. Unless otherwise specified, stage commands operates on the selected stage.

The stage indicator `{somestage}` is display in front of your shell prompt, it shows the name of the selected stage.

If you create a new stage, Stool changes the current working directory to the newly created stage directory. Thus, the new stage
becomes the selected stage. `stool select` changes the current working directory to the respective stage directory,
thus is just a convenience way for cd'ing between stage directories.

The stage indicator is red when you're not the owner of the selected stage. It is blue, when the
selected stage is broken or no longer exists. The stage indicator is invisible if you have no stage selected;
select a stage to set a stage indicator.

### Stage owner xml:id="stageOwner"

Stool has a configuration property `shared`. If you run Stool on your own machine
and you are the only user, you'll set it to `false` to indicate that you are the only user
of the stages you create. In this case, you can skip the rest of this section. On the other hand, if Stool
will be used by multiple users and you want all users to work on all stages, you'll set `shared`
to `true` to use Stool as described in this sections.

If you create a new stage, you become the owner of this stage. You own the files in the stage directory
in terms of Unix file ownership, and you have write access to stage files. Other users have read access to the
files. Note that everybody (with the appropriate scm permissions) can change files in Subversion or git, but only
the stage owner can run `svn up` or `git pull` on his stage.

Starting a stage starts a Tomcat process owned by the stage owner, no matter who
actually issued the start command. Thus, any user can start and stop a stage, not only the owner.

If a different user has to make changes to stage files, he/she has to use the `chown`
command to become owner of the stage and thus get the necessary permissions to change files.

Example: If user `mhm` owns stage`tec1584`, and user
`bitterichc` wants to change a file in the stage directory, one of them has to run
`stool chown -stage tec1584 bitterichc`. This will stop any stage processes (which is allowed
for `bitterichc` as well!), change Unix file ownership of the stage files to
`bitterichc` and re-start the stage processes to also change process ownership to the new
owner.

Rationale:
<itemizedlist>
  <listitem>
Tool users like `servlet` or `stage5` have to be replaced by
personalized logins (security guideline).
  </listitem>
  <listitem>
We need to know exactly who changed a stage file (security guideline).
  </listitem>
  <listitem>
It's difficult and fragile to grant multiple users write access to stage files (e.g. via umask
configuration), in particular because the respective user's home directory has to be private.
  </listitem>
</itemizedlist>
Ownership is meant to track changes, not to prevent them.

Implementation note: The `chown` command internally uses `sudo` to elevate the current user's 
permissions to start/stop processes or change file ownership. Note that some files have to be group-writable, because arbitrary
users have to create them, in particular things generated by`stool start`). All users of Stool
have to be in the group `stool`.

### Properties xml:id="properties"

Stool is configured via properties. A property is a key/value pair. Value has a type (string, number, date,
boolean, list (of strings), or map (string to string)). Stool distinguishes Stool properties and stage
properties. Stool properties are global settings that apply to all stages, they are usually adjusted by system
administrators (see <link linkend="stoolProperties">stool properties</link>). Stage properties configure the
respective stage only, every stage has its own set of stage properties. The owner of a stage can adjust stage
properties with <link linkend="stoolConfig">`stool config`</link>.

### Backstage

Every stage directory contains backstage directory `.backstage` that stores Stool-related
data about the stage, e.g. the stage properties, Tomcat configuration and log files of the applications. The
backstage directory is created when you create or import the stage. `$STOOL_HOME/backstages`
contains a symlink <replaceable>id</replaceable>-><replaceable>backstage</replaceable>. Stool uses this
directory to iterate all stages.

Stool removes backstage symlinks either explicitly when you run`stool remove`, or
implicitly when it detects that the target directory has been removed. Stool checks for - and cleans - stale
backstage links before every command.

### Stage Exiring xml:id="stageExpiring"

Every stage has an `expire` property that specifies how long the stage is needed. You can
see the expire date with `stool config expire`. If this date has passed, the stage is called
expired, and it is automatically stopped, the owner gets an email notification and you cannot start it again
unless you specify a new date with `stool config expire=`<replaceable>yyyy-mm-dd</replaceable>.

Depending on the `autoRemove` Stool property, the stage will automatically be removed after
the configured number of days. Stage expiring helps to detect and remove unused stages, which is crucial for
shared machines. If you receive an email notification that your stage has expired, please check if your stage
is still needed. If so, adjust the expire date, otherwise remove the stage.

### User defaults

Users can define default values for various option by placing a properties file `.stool.defaults` in
their home drectory. If this file exists, Stool uses the contained properties as default values for various options.
For example, a property `refresh.build=true` causes `stool refresh` to build a stage without
explicitly specifing the `-build` option. (Note: To override this default, use `stool refresh -build=false`).

Supported user default properties:

* <option>verbose</option>
  controls the `-v` option for every Stool command
* <option>exception</option>
  controls the `-e` option for every Stool command
* <option>auto.restart</option>
  controls the `-autorestart` option for every stage command
* <option>auto.stop</option>
  controls the `-autostop` option for every stage command
* <option>auto.rechown</option>
  controls the `-autorechown` option for every stage command
* <option>auto.restart</option>
  controls the `-autochown` option for every stage command
* <option>import.name</option>
  controls the `-name` option for the import command
* <option>import.max</option>
  controls the `-max` option for the import command
* <option>history.max</option>
  controls the `-max` option for the history command
* <option>tomcat.debug</option>
  controls the `-debug` option for the start and restart command
* <option>tomcat.suspend</option>
  controls the `-suspend` option for the start and restart command
* <option>list.defaults</option>
  controls the `-defaults` option for the list command
* <option>status.defaults</option>
  controls the `-defaults` option for the status command
* <option>select.fuzzy</option>
  controls the `-fuzzy` option for the select command
* <option>refresh.build</option>
  controls the `-build` option for the refresh command


### Dashboard

The dashboard is a system stage you can install to control stages via browser.

## Commands

### stool

      <refentry>
        <refnamediv>
          <refname>stool</refname>
          <refpurpose>Stage tool</refpurpose>
        </refnamediv>

        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool</command>
            <arg rep="repeat">
              <replaceable>global-option</replaceable>
            </arg>
            <arg>
              <replaceable>command</replaceable>
            </arg>
            <arg rep="repeat">
              <replaceable>arguments</replaceable>
            </arg>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Stool is a command line tool that provides a lifecycle for stages: create, configure, build, start,
stop and remove. A stage contains web applications built from source or available as artifacts.
<replaceable>command</replaceable> defaults to `help`.

#### Commands

<xi:include href="../../../target/synopsis.xml"/>

#### Global options

* <option>-v</option> enables verbose output
* <option>-e</option> prints stacktrace for all errors
* <option>-svnuser</option> specifies the user name for svn operations
* <option>-svnpassword</option> specifies the password for svn operations

#### Stool Properties
        
        <refsect1 xml:id="stoolProperties">

Stool's global configuration is stored in `$STOOL_HOME/config.json`. It defines following
<link linkend="properties">properties</link>.

* <term>admin</term> 
  Email of the person to receive validation failures and exception mails. Empty to disable these emails.
  Type string. Example: `Max Mustermann &lt;max@mustermann.org>`.
* <term>autoRemove</term>
  Days to wait before removing an expired stage. -1 to disable this feature. Type number. 
* baseHeap
  Defines how to compute the initial `tomcat.heap` property for new stages:
  `baseHeap` mb for every application. Type number.
* <term>certificates</term>
  Url to generate certificates to make stages available via https. Empty to generate self-signed
  certificates. Otherwise, Stool generates a `csr` and sends a post request to
  <replaceable>certificates</replaceable>`/stagename`, expecting back the certificate. Type string.
* <term>committed
  `true` if users have to commit source changes before Stool allows them to start the stage. Type boolean.
* <term>defaults</term>
  Default values for stage properties. Type map.
* <term>diskMin</term>
  Minimum mb free space. If less space is available on the target partition, Stool refuses to create new stages. Type number. 
* <term>downloadTomcat</term>
  Url pattern where to download Tomcat. Available variables: `${version}` and `${major}`. Type string.
* <term>downloadServiceWrapper</term>
  Url pattern where to download Java Service Wrapper. Available variables: `${version}` and `${major}`. Type string.
* <term>downloadCache
  Directory where to store Tomcat or Java Service Wrapper downloads. Type string.
* <term>hostname</term>
  Fully qualified hostname used to refer to this machine in application urls and emails. Type string.
* <term>ldapCredentials</term>
  Password for Ldap authentication. Ignored if ldap is disabled. Type string.
* <term>ldapPrincipal</term>
  User for Ldap authentication. Ignored if ldap is disabled. Type string.
* <term>ldapSso</term>
  To authenticate Dashboard users. Type string.
* <term>ldapUrl</term>
  Ldap url for user information. Empty string to disable ldap. Type string.
* <term>macros</term>
  String replacements for stage properties.
  Stool automatically defines `directory` for the respective stage directory,
  `localRepository` for the local Maven repository of this stage,
  `svnCredentials` as expected by `svn`, and `stoolSvnCredentials`
  as expected by `stool`. Type map.
* <term>mailHost</term>
  Smtp Host name to deliver emails. Empty to disable. Type string.
* <term>mailUsername</term>
  Username for mailHost. Type string.
* <term>mailPassword</term>
  Password for mailHost. Type string.
* <term>portFirst</term>
  First port available for stages. Has to be an even number >1023. Type number.
* <term>portLast</term>
  Last port available for stages. Has to be an odd number >1023. Type number.
* <term>quota</term>
  Megabytes available for stages. The sum of all stage quota properties cannot exceed this number. 0 disables this
  feature. You'll usually set this to the size of the partition that will store your stages. Note that this quota
  cannot prevent disk full problem because stages can be placed on arbitrary partitions. Type number. 
* <term>shared</term>
  `true` if multiple user may work on stages. See <link linkend="stageOwner">stage owner</link>
  for details. Type boolean.
* <term>search</term>
  Command line to execute if `stool create` is called with an % url.
  When calling the command, the placeholder `()` is replaced by the url.
  Default is empty which disables this feature.
* <term>vhosts</term>
  `true` to create application urls with vhosts for application and stage name.
  `false` to create application urls without vhosts. (Note that urls always contain the port to
  distinguish between stages). Type boolean. If you want to enable vhosts you have to make sure you
  have the respective DNS * entries for your machine.

#### Environment

`STOOL_OPTS` to configure the underlying JVM.

`http_proxy`, `https_proxy` and `no_proxy` to configure proxy settings.

#### See Also

Homepage: https://github.com/mlhartme/stool

Documentation: http://mlhartme.github.io/stool/documentation/documentation.html

Invoke `stool help` <replaceable>command</replaceable> to get help for the specified command.


## System commands

Commands that do not deal with individual stages.

### stool help

        <refnamediv>
          <refname>stool-help</refname>
          <refpurpose>Print man page</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool help</command>
            <arg>
              <replaceable>command</replaceable>
            </arg>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Prints help about the specified <replaceable>command</replaceable>. Or, if <replaceable>command</replaceable>
is not specified, prints help about Stool.

### stool version

        <refnamediv>
          <refname>stool-version</refname>
          <refpurpose>Print version info</refpurpose>
        </refnamediv>

        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool version</command>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Prints Stool's version info. Use the global `-v` option to get additional diagnostic info.

### stool system start

        <refnamediv>
          <refname>stool-system-start</refname>
          <refpurpose>Startup all stages</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool system-start</command>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Start all system stages and all sleeping stages. Always uses fail mode `after`.


### stool system stop
        <refnamediv>
          <refname>stool-system-stop</refname>
          <refpurpose>Shutdown stages</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool system-stop</command>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Stop all system stages and sends all other running stages to sleep. Always uses fail mode `after`.

### stool create

        <refnamediv>
          <refname>stool-create</refname>
          <refpurpose>Create a new stage</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool create</command>
            <group>
              <arg>-quiet</arg>
            </group>
            <arg choice="plain">
              <replaceable>url</replaceable>
            </arg>
            <arg>
              <replaceable>directory</replaceable>
            </arg>
            <arg rep="repeat">
              <replaceable>key=value</replaceable>
            </arg>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Creates a new stage- and backstage directory and enters the stage directory. In most cases, you can invoke `stool create` 
similar to`svn checkout`: with an url and a directory to checkout to.

<replaceable>url</replaceable> specifies the application you want to run in your stage. In many cases, the url is a subversion url prefixed
with `svn:` or a git url prefixed with `git:`. Stool performs a checkout resp. a clone. Output of the 
command is printed to the console unless the `-quiet` option is specified.

To create an artifact stage, specify a war file, a file url or a GAV url. You may specify multiple comma-separated urls, and you may 
specify `=`<replaceable>name</replaceable> if you want to assign a non-default vhost for an application.

Instead of a <replaceable>url</replaceable> you can specify `%`<replaceable>searchstring</replaceable>. This will search 
the configured search tools for the specified string, show all matching scm urls, and ask you to select one.

<replaceable>directory</replaceable> specifies the stage directory to hold your application. If not specified, the current directory
with the last usable segment of the `url` (i.e. the last segment that is not trunk, tags, or branches) is used. You can 
specify an arbitrary directory, as long as it does not exist yet and the parent directory exists and is writable for all users of the Stool 
group. Otherwise, create reports an error.

The new stage is configured with default stage properties. You can specify <replaceable>key-value</replaceable> pairs to override the 
defaults, or you can change the configuration later with <link linkend="stoolConfig">`stool config`</link>.

For artifact stages, the `maven.home` property is used to locate Maven settings which configure the repositories (and 
optional credentials) to download for artifact(s) from.

The stage name property defaults to the directory name, i.e. the last segment of the absolute path to the stage directory.

`create` reports an error if the available free disk space is low (the threshold is specified by the `diskMin` 
Stool property.


#### Examples

Create an artifact stage: `stool create gav:net.oneandone:hellowar:1.0.3`

Create an artifact stage from a file: `stool create file:///my/path/to/artifact.war`

Create an artifact stage with multiple applications: `stool create gav:net.oneandone:hellowar:1.0.2,gav:net.oneandone:hellowar:1.0.3=second`

Create a source stage from git: `stool create git:git@github.com:mlhartme/hellowar.git`

Create a source stage from svn: `stool create svn:https://github.com/mlhartme/hellowar/trunk`

### stool import

        <refnamediv>
          <refname>stool-import</refname>
          <refpurpose>Create stages for one or many existing directories</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool import</command>
            <arg>-max
              <replaceable>n</replaceable>
            </arg>
            <arg>-name
              <replaceable>template</replaceable>
            </arg>
            <arg rep="repeat">
              <replaceable>directory</replaceable>
            </arg>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Scans <replaceable>directory</replaceable> for stage candidates and offers to import them. The candidates you
select will be imported, i.e. a backstage directory for the stage directory is created. If the scan only
yields a single candidate, it will be imported and selected without additional interaction.

<replaceable>template</replaceable>
is a string defining the stage name. And any occurrence of `%d`
will be replaced by the current directory name. Default template is`%d`.


### stool switch

        <refnamediv>
          <refname>stool-select</refname>
          <refpurpose>Switch between stages</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool select</command>
            <group>
              <arg choice="plain">
                <replaceable>stage</replaceable>
              </arg>
              <arg choice="plain">none</arg>
            </group>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Prints the selected stage when called without argument.

Otherwise cds to the stage directory of the specified <replaceable>stage</replaceable>.

When called with `none`: cds to the parent directory of the current stage.

If the specified stage is not found, the command prints an error message and lists stages that
you could have meant. If you also specified the `-fuzzy` option and there's only a
single candidate, this stage will be selected.

## Stage Commands

Most Stool commands are stage commands, i.e. they operate on one or multiple stages. Typical
stage commands are `status`, `build`, `start`, and
`stop`. Note that `create` is not a stage command because it does not
initially have a stage to operate on (although it results in a new (and selected) stage).

All stage commands provide stage options, invoke `stool help stage-options` for documentation.

### stage options

      <refentry xml:id="stageOptions">
        <refnamediv>
          <refname>stool-stage-options</refname>
          <refpurpose>Options available for all stage command</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool</command>
            <arg choice="plain">
              <replaceable>stage-command</replaceable>
            </arg>
            <group>
              <arg choice="plain">-all</arg>
              <arg choice="plain">-stage <replaceable>predicate</replaceable></arg>
            </group>
            <group>
              <arg choice="plain">-fail <replaceable>mode</replaceable></arg>
            </group>
            <group>
              <arg choice="plain">-autochown</arg>
              <arg choice="plain">-autorechown</arg>
              <arg choice="plain">-autostop</arg>
              <arg choice="plain">-autorestart</arg>
            </group>
            <arg choice="plain">command-options</arg>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Selection options

By default, stage commands operate on the selected stage (as shown in the stage indicator). You can change this by specifying a selection option.

`-all` operates on all stages

`-stage` <replaceable>predicate</replaceable> operates on matching stages. The syntax for predicates is as follows:
            <programlisting>
              or = and {',' and}
              and = expr {'+' expr}
              expr = NAME | cmp
              cmp = (FIELD | PROPERTY) ('=' | '!=') (VALUE | prefix | suffix | substring)
              prefix = VALUE '*'
              suffix = '*' VALUE
              substring = '*' VALUE '*'
              NAME       # name of a stage
              FIELD      # name of a status field
              PROPERTY   # name of configuration property
              VALUE      # arbitrary string
            </programlisting>

The most basic predicate is a simple `NAME`. It matches only on the specified stage. This is handy
to invoke one command for a stage without selecting it.

Next, a predicate <replaceable>FIELD</replaceable>`=`<replaceable>VALUE</replaceable> matches
stages who's status field has the specified value.

<replaceable>PROPERTY</replaceable>`=`<replaceable>VALUE</replaceable> is similar, it matches stage properties.

#### Failure mode

Since stage commands operate on an arbitrary number of stages, you might want to specify what to do if the command
fails on some stages. That's what `-fail` <replaceable>mode</replaceable> is for.

Mode `normal` reports problems immediately and aborts execution, Stool does not try to
invoke the command on remaining matching stages. This is the default.

`after` reports problems after the command was invoked on all matching stages.

`never` is similar to `after`, but reports warnings instead of errors
(and thus, Stool always returns with exit code 0).

#### Auto options

Stage commands provide auto options do deal with stages that are not stopped or not owned by the current user.

With `-autorechown`, Stool checks the owner of a stage. If it is not the current user, it temporarily
chowns the stage to the current user, invokes the actual command, and chowns the stage back to the original
owner. `-autochown` is similar, the stage is not chowned back.

With `-autorestart`, Stool checks the state of a stage. It the stage is up, Stool stops the stage,
invokes the actual command, and starts the stage again. `-autostop` is similar, but the stage is not started
again.

#### Examples

`stool status -stage foo` prints the status of stage `foo`.

`stool config -stage tomcat.version!=7.0.57 tomcat.version` prints all Tomcat versions other than 7.0.57.

`stool start -all -fail after` starts all stages. Without `-all`, the command would abort
after the first stage that cannot be started (e.g. because it's already running).

`stool stop -stage state=up` stops all stages currently up, but aborts immediately if one stage fails to stop.

### stool build
      
        <refnamediv>
          <refname>stool-build</refname>
          <refpurpose>Build a stage</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool build</command>
            <arg rep="repeat">
              <replaceable>stage-option</replaceable>
            </arg>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Enters the stage directory, sets MAVEN_HOME, MAVEN_OPTS and JAVA_HOME as configured for the stage and executes the build command
specified in the `build` property. Reports an error if the stage is not owned or if the stage is up.

You can see the configured build command with`stool config build`, and you can change it with
`stool config "build="`
<replaceable>your command command</replaceable>
`"`
The quotes are mandatory if your command contains spaces.

The pre-defined build command for artifact stages does nothing. Thus, you can invoke
`stool build` for artifact stages, it just has no effect.

If you invoke <command>build</command> from the dashboard application, the build command executes in the environment
defined for the dashboard stage with the additional environment variables mentioned above.

If you work locally on your own machine, you'll normally prefer to directly invoke your build command. However, you have
to use `stool build` because shared machine have a separate local Maven repository for every stage.

<xi:include href="stageOptions.xml" parse="xml"/>


### stool remove

        <refnamediv>
          <refname>stool-remove</refname>
          <refpurpose>Remove a stage</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool remove</command>
            <arg rep="repeat">
              <replaceable>stage-option</replaceable>
            </arg>
            <arg>-force</arg>
            <arg>-batch</arg>
            <arg>-backstage</arg>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Removes the stage, i.e. deletes the stage directory and the backstage directory.

Reports an error is the stage is up or if the stage has uncommitted changes. In this case, stop the stage and inspect
the uncommitted changes; either commit them or revert them. Alternatively, you can disable this check with the
`-force` option.

Also, before removing anything, this command asks if you really want to remove the stage. You can suppress this interaction with the 
`-batch` option.

If you specify the `-backstage` option, only the backstage directory will be deleted and the stage is
removed from Stool's list of stages. This is useful to "unimport" a stage, i.e. revert the effect of `stool import`.

Changes the current directory to the parent of the now deleted stage directory.

<xi:include href="stageOptions.xml" parse="xml"/>


### stool start

        <refnamediv>
          <refname>stool-start</refname>
          <refpurpose>Start a stage</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool start</command>
            <arg rep="repeat">
              <replaceable>stage-option</replaceable>
            </arg>
            <group>
              <arg choice="plain">-debug</arg>
              <arg choice="plain">-suspend</arg>
            </group>
            <arg>-tail</arg>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Creates the necessary configuration and starts Tomcat with all applications for this stage. If the stage is an artifact stage, you can 
start it right away; otherwise, you have to build it first. Depending on your applications, startup may take a while.

Any user may start a stage. The processes started by this command belong to the stage owner, not the user running the start command.

Startup is refused if your stage is expired. In this case, use `stool config expire=`<replaceable>newdate</replaceable>
to configure a new `expire` date.

Startup is also refused if your stage quota is exceeded. In this case, delete some unused files, try `stool cleanup` or 
`stool config quota=`<replaceable>n</replaceable>.

`-debug` and `-suspend` enable the debugger. The difference is that `-suspend`
waits for the debugger to connect before starting any application code.

Use the `-tail` option to start tomcat and get `catalina.out` printed to the console.
Press ctrl-c to stop watching `catalina.out`, the application will continue to run. Alternatively, you can tail
the current stage manually with `stool cd logs &amp;&amp; tail -f catalina.out`

`start` generates a Tomcat base directory <replaceable>backstage</replaceable>`/tomcat` if it
does not yet exist. If it exists, only the server.xml is updated by taking server.xml.template and adding all apps to it. This
allows for manual changes in the base directory. `start` deletes all files in Tomcat's `temp`
directory.

The Tomcat version is configurable with `stool config tomcat.version=`<replaceable>version</replaceable>.
If you change it, you have to stop the stage, delete the <replaceable>backstage</replaceable>`/tomcat`
directory and start the stage. The respective Tomcat will be downloaded automatically to the directory specified by the global
`downloadCache` property (default is `$STOOL_HOME/downloads`). Alternatively,
you can place customized Tomcats into this directory, provided they unpack to a directory that matches the base file name of
the `tar.gz` file.

Technically, Tomcat is started by the Java Service wrapper (http://wrapper.tanukisoftware.com/). You can configure the
version of the wrapper with the `tomcat.service` property.

If you want to re-generated all files generated by this command, use `stool cd backstage &amp;&amp; rm -rf
service ssl tomcat`. This is useful e.g. to get certificates regenerated.

The environment of the started application is the environment specified by the `tomcat.env` property. In
addition, Stool defines a `USER` variable set to the stage owner and a `HOME` variable
pointing to the owner's home directory.

<xi:include href="stageOptions.xml" parse="xml"/>


### stool stop

        <refnamediv>
          <refname>stool-stop</refname>
          <refpurpose>Stop a stage</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool stop</command>
            <arg rep="repeat">
              <replaceable>stage-option</replaceable>
            </arg>
            <arg>-sleep</arg>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Stops Tomcat of the respective stage. If `-sleep` is specified, the stage is also marked as sleeping.

This command signals Tomcat to shutdown all applications and waits for up to 4 minutes to complete this. After this timeout,
Tomcat is killed (with -9). If Tomcat shutdown is slow, try to debug the applications running in this stage and find out why
the don't obey to the shutdown request. 

<xi:include href="stageOptions.xml" parse="xml"/>


### stool restart

        <refnamediv>
          <refname>stool-restart</refname>
          <refpurpose>Restart a stage</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool restart</command>
            <arg rep="repeat">
              <replaceable>stage-option</replaceable>
            </arg>
            <group>
              <arg choice="plain">-debug</arg>
              <arg choice="plain">-suspend</arg>
            </group>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Shorthand for `stool stop` and `stool start` with the specified options.

<xi:include href="stageOptions.xml" parse="xml"/>


### stool refresh

        <refnamediv>
          <refname>stool-refresh</refname>
          <refpurpose>Refresh a stage</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool refresh</command>
            <arg rep="repeat">
              <replaceable>stage-option</replaceable>
            </arg>
            <group>
              <arg choice="plain">-build</arg>
              <arg choice="plain">-restore</arg>
            </group>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Reports an error if the stage is not owned or if the stage is up.

For artifact stages: check for new artifacts and installs them if any.

For source changes: invokes the command specified by the `refresh` property. If `-build`
is specified, also runs the command specified by the `build` property.

<xi:include href="stageOptions.xml" parse="xml"/>


### stool chown

        <refnamediv>
          <refname>stool-chown</refname>
          <refpurpose>Change the stage owner</refpurpose>
        </refnamediv>

        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool chown</command>
            <arg rep="repeat">
              <replaceable>stage-option</replaceable>
            </arg>
            <group>
              <arg>-batch</arg>
              <arg>
                <replaceable>user</replaceable>
              </arg>
            </group>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Changes the stage owner, i.e. the owner of all files and directories in the stage directory.

Before executing, `chown` checks if the stage has modified source files. If so, it asks for confirmation
before changing ownership. You can skip confirmation by specifying`-batch`.

Reports an error if the stage is up. In this case, you can specify `-autostop` or
`-autorestart` to stop the stage before changing ownership and also start it afterwards.

<replaceable>user</replaceable> defaults to the current user.

<xi:include href="stageOptions.xml" parse="xml"/>

### stool history

        <refnamediv>
          <refname>stool-history</refname>
          <refpurpose>Print the command history</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool history</command>
            <arg rep="repeat">
              <replaceable>stage-option</replaceable>
            </arg>
            <arg>-max
              <replaceable>n</replaceable>
            </arg>
            <arg rep="repeat">
              <replaceable>detail</replaceable>
            </arg>
          </cmdsynopsis>

#### Description

Prints the command history of the stage. Specify <replaceable>detail</replaceable> with a command number or a command
range to get the full command output for the respective command(s). If the max number
<replaceable>n</replaceable> of commands is exceeded, older commands are ignored (<replaceable>n</replaceable> defauls is 999).

<xi:include href="stageOptions.xml" parse="xml"/>


### stool cd

        <refnamediv>
          <refname>stool-cd</refname>
          <refpurpose>Jump to directory</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool cd</command>
            <arg rep="repeat">
              <replaceable>stage-option</replaceable>
            </arg>
            <arg>
              <replaceable>target</replaceable>
            </arg>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Changes the current working directory to the specified <replaceable>target</replaceable>:

* <term>(empty)</term> the stage directory
* <term>backstage</term> the backstage directory.
* <term>(otherwise)</term> the specified direct or indirect sub-directory of the backstage directory.

<xi:include href="stageOptions.xml" parse="xml"/>

#### Example

`stool cd logs` will jumps to `tomcat/logs` inside your backstage directory.

### stool config

        <refnamediv>
          <refname>stool-config</refname>
          <refpurpose>Manage stage properties</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool config</command>
            <arg rep="repeat">
              <replaceable>stage-option</replaceable>
            </arg>
            <group rep="repeat">
              <arg>
                <replaceable>key</replaceable>
              </arg>
              <arg><replaceable>key</replaceable>=
                <replaceable>value</replaceable>
              </arg>
            </group>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

This command gets or sets stage <link linkend="properties">properties</link>. Caution: `config` does
not deal with stool properties, see `stool help` for that.

When invoked without arguments, all stage properties are printed.

When invoked with one or more <replaceable>key</replaceable>s, the respective properties are printed.

When invoked with one or more assignments, the respective properties are changed.

Property values may contain {} to refer to the previous value. You can use this, e.g., to append to a property:
`stool config "tomcat.opts={} -Dfoo=bar"`.

If you want to set a property to a value with spaces, you have to use quotes around the key-value pair.
Otherwise, the shell does not see what belongs to your value.

If you change a property, you have to invoke the necessary re-builds or re-starts to make the changes
effective. E.g. if you change `tomcat.heap`, you have to run `stool restart`
to make the change effective.

Properties have a type: boolean, number, date, string, list of strings, or map of strings.

Boolean properties have the values `true` or `false`, case sensitive.

Date properties have the form <replaceable>yyyy-mm-dd</replaceable>, so a valid value for
`expire` is - e.g. -`2016-12-31`.

List properties (e.g.`tomcat.select`) are separated by commas, whitespace before and after an item is ignored.

Map properties (e.g.`tomcat.env`) separate entries by commas, whitespace before and after is ignored.
Each entry separates key and value by a colon. Example `PATH:/bin, HOME:/home/me`

<xi:include href="stageOptions.xml" parse="xml"/>

#### Available stage properties

Note that the default values below might be overwritten by Stool defaults on your system.

* <term>autoRefresh</term>
  True if you want the dashboard to automatically refresh the stage every minute. Type boolean.
* <term>build</term>
  Shell command executed if the user invokes `stool build`. Type string.
* <term>comment</term>
  Arbitrary comment for this stage. Stool only stores this value, it has no effect. Type string.
* <term>cookies</term>
  Enable or disable cookies. Type boolean. Default value: `true`
* <term>expire</term>
  Defines when this stage <link linkend="stageExpiring">expires</link>. Type date.
* <term>java.home</term>
  Install directory of the JDK used to build and run this stage. Type string.
* <term>maven.home</term>
  Maven home directory used to build this stage or resolve artifacts. Type string.
* <term>maven.opts</term>
  MAVEN_OPTS when building this stage. Type string. Default value: (empty)
* <term>notify</term>
  List of email address or `@owner` or `@creator` to send notifications about
  this stage. Type list. Default value: @owner, @creator.
* <term>pom</term>
  Path of the pom file in the stage directory. Type string. Default value: `pom.xml`.
* <term>prepare</term>
  Shell command executed after initial checkout of a source stage. Type string.
* <term>refresh</term>
  Shell command executed for source stage if the user invokes `stool refresh`.
  Type string. Default value: `svn @svnCredentials@ up`
* <term>quota</term>
  Max disk space for this stage in mb. You cannot start stages if this space exceeded.
  The sum of all quotas cannot exceed the stool quota. Type number.
* <term>tomcat.env</term>
  The environment to start Tomcat with. Type map. This is intentionally not the environment of the
  current user because any user must be able to start the stage and get the same behavior.
* <term>tomcat.opts</term>
  CATALINA_OPTS without heap settings. Type string. Default value: (empty)
* <term>tomcat.heap</term>
  Java heap memory ("-Xmx") in mb when running Tomcat. Type number.
* <term>tomcat.select</term>
  List of selected applications. When starting a stage, Stool configures tomcat only for the selected
  applications. If none is selected (which is the default), it configures all applications. Type list.
  Default value: `` (empty)
* <term>tomcat.service</term>
  Version of the Java Service Wrapper to use. Default value: `3.5.29`. Type string.
* <term>tomcat.version</term>
  Tomcat version to use. Type string. Default value: `8.5.3`. If you change this property,
  you have to stop tomcat, delete the `.backstage/tomcat` directory, and start Tomcat again.
* <term>url</term>
  A pattern that define how to build the application urls: a sequence of strings and alternatives, where
  alternatives a strings in brackes, separated by |. Example: `(http|https)://%a.%s.%h:@p/foo//bar`
  Strings may contain place holders: %a for the application name, %s for the stage name, %h for the hostname,
  and %p for the port. A double slash in the path part of the url separates the web application context from a normal path
  suffix.

#### Examples

`stool config tomcat.heap` prints the current value for Tomcat heap space.

`stool config tomcat.heap=1000` sets the tomcat heap size to `1000` mb.

`stool config "build=mvn clean package"` sets a value with spaces.

`stool config tomcat.select=foo,bar` configures a list property. Do not use spaces around
the comma because the shell would consider this as a new key-value argument -- or quote the whole argument.

### stool move

        <refnamediv>
          <refname>stool-move</refname>
          <refpurpose>Move the stage directory</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool move</command>
            <arg rep="repeat">
              <replaceable>stage-option</replaceable>
            </arg>
            <arg choice="plain">
              <replaceable>dest</replaceable>
            </arg>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Moves the stage directory without touching the stage id or stage name. If <replaceable>dest</replaceable>
exists, it is moved into it. Otherwise it is moved into the parent of dest with the specified name. This is the same behavior
as the unix `mv` command, but it also adjusts Stool's backstage directory.

You might have to re-build your application after moving the stage if you have development tools that store absolute paths
(e.g. Lavender ...).

<xi:include href="stageOptions.xml" parse="xml"/>

### stool port

        <refnamediv>
          <refname>stool-port</refname>
          <refpurpose>Allocates ports for the current stage</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool port</command>
            <arg rep="repeat">
              <replaceable>stage-option</replaceable>
            </arg>
            <group>
              <arg rep="repeat"><replaceable>application</replaceable>=<replaceable>port</replaceable></arg>
            </group>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Allocates the specified ports for this stage. <replaceable>application</replaceable> specifies the application to use this port.
<replaceable>port</replaceable> is the http port, <replaceable>port</replaceable>`+1` is automatically reserved
for https. When starting a stage, unused allocated ports are freed.

<xi:include href="stageOptions.xml" parse="xml"/>


### stool status 

        <refnamediv>
          <refname>stool-status</refname>
          <refpurpose>Print stage status</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool status</command>
            <arg rep="repeat">
              <replaceable>stage-option</replaceable>
            </arg>
            <arg rep="repeat" choice="plain">
              <replaceable>field</replaceable>
            </arg>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Prints the specified status <replaceable>field</replaceable>s of the stage. Default: print all fields.

A field may be any stage property or one of the following status fields:

* <term>apps</term>
  Application urls this stage.
* <term>backstage</term>
  Absolute path of the backage directory. Type string.
* <term>buildtime</term>
  Last modified date of the war files for this stage.
* <term>cpu</term>
  Cpu utilization for this stage reported by `ps`.
* <term>creator</term>
  User who created this stage. Type string.
* <term>debugger</term>
  Debugger port or empty if not running with debugger.
* <term>directory</term>
  Absolute path of the stage directory. Type string.
* <term>disk</term>
  Disk space used for this stage in mb. Type number.
* <term>id</term>
  Unique identifier for this stage. Type string.
* <term>jmx</term>
  Some jmx tool invocations for this stage.
* <term>mem</term>
  Memory utilization reported by ps for this stage.
* <term>others</term>
  Other urls this stage.
* <term>owner</term>
  Owner of this stage. Type string.
* <term>selected</term>
  `true` if this is the selected stage. Type boolean.
* <term>service</term>
  Java Service Wrapper process id or empty if not up. 
* <term>suspend</term>
  `true` if running with suspend.
* <term>state</term>
  `down`, `sleeping` or `up`. Type string.
* <term>tomcat</term>
  Tomcat process id or empty if not up.
* <term>uptime</term>
  How long this stage is running.
* <term>type</term>
  `source` or `artifact`. Type string.
* <term>url</term>
  Url of this stage. Type string.

<xi:include href="stageOptions.xml" parse="xml"/>

### stool list

        <refnamediv>
          <refname>stool-list</refname>
          <refpurpose>List stages</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool list</command>
            <arg rep="repeat">
              <replaceable>stage-option</replaceable>
            </arg>
            <arg rep="repeat" choice="plain">
              <replaceable>field</replaceable>
            </arg>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Prints a short status of all stages (or the stages specified by `-stage`). See the `status`
command for a list of available fields. Default fields are `state ower url directory`.

<xi:include href="stageOptions.xml" parse="xml"/>

        <refnamediv>
          <refname>stool-cleanup</refname>
          <refpurpose>Cleanup a stage</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool cleanup</command>
            <arg rep="repeat">
              <replaceable>stage-option</replaceable>
            </arg>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Removes the Maven repository and rotates *.log info *.log.gz files.

<xi:include href="stageOptions.xml" parse="xml"/>


### stool validate

        <refnamediv>
          <refname>stool-validate</refname>
          <refpurpose>Validate the stage</refpurpose>
        </refnamediv>
        <refsynopsisdiv>
          <cmdsynopsis>
            <command>stool validate</command>
            <arg rep="repeat">
              <replaceable>stage-option</replaceable>
            </arg>
            <arg>-email</arg>
            <arg>-repair</arg>
          </cmdsynopsis>
        </refsynopsisdiv>

#### Description

Checks if the `expire` date of the stage has passes or the `quota` exceeded. If so, and if
`-repair` is specified, the stage is stopped (and also removed if expired for more than autoremove days). And
if `-email` is specified, a notification mail is sent to the stage owner.

Also checks DNS settings.

Also performs log rotation: logs a gzipped and removed after 90 days.

Also checks Stool's locking system for stale locks and, if `-repair` is specified, removed them.

<xi:include href="stageOptions.xml" parse="xml"/>

## Setup

Prerequisites:
* Linux or Mac
* Java 8 or higher. This is prerequisite because Stool is implemented in Java 8. Howevery, you can build your stages with an
  arbitrary Java version.

First of all: Stool is split into `stool` itself and the `dashboard`. The dashboard is optional
if you want some of Stool's functionality available in a browser.

Next, you have to choose the appropriate of setup for your machine: isolated or shared.


### Isolated setup

Isolated setup means that stages created by one user can only be seen and used by this user. Security: everything executes
as the respective user, no `root` permissions or sudo rules required.

Steps:
* Download the latest `application.sh` file from
  <link xlink:href="http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22net.oneandone.stool%22%20AND%20a%3A%22main%22">Maven Central</link>.
* Make it executable, rename it to `stool` and add it to your $PATH.
* Run `stool setup` to create Stool's home directory (`~/.stool`).
* Source `~/.stool/shell.rc` in your shell initialization file (e.g. `~/.bash_profile`).
* Adjust `~/.stool/config.json` to your needs: see <link linkend="stoolProperties">stool properties</link><
* Optional: setup a cron job to run `stool validate -all -email -repair` every night.

### Shared setup

Shared setup means that stages can be created, modified and removed by every other user on the machines.

Security: you need root permission to setup Stool in shared mode. Having shared Stool on you machine allows every Stool user
to execute arbitrary code as arbitrary Stool user (i.e. in a stage started by him or a Tomcat/Java Service Wrapper provided by him).
In addition, Stool users can change arbitrary files in any of the stages (via stool chown).

Debian package are available from <link xlink:href="http://central.sonatype.org">Maven Central</link>
(I'd like to have a public Debian repository instead, by I don't know who would host this for free). To install Stool:

* Download the latest `deb` from
  <link xlink:href="http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22net.oneandone.stool%22%20AND%20a%3A%22setup%22">Maven central</link>.
* Run `dpkg -i stool-x.y.z.deb`
* Optional, only if you want the dashboard: repeat the previous steps for setup-x.y.z-dashboard.deb
* Adjust stool properties with `sudo nano /usr/share/stool-3.4/config.json`.
* Restart your shell or re-login if you work on a VM/remote machine. (Otherwise, the stage indicator will not work properly
  and Stool cannot change the current directory).
* Invoke `stool` to verify your setup. You should get a usage message.

## Directory Layout

... of $STOOL_HOME: either /usr/share/stool-3.4 or ~/.stool

      <programlisting>
        |- config.json (Stool configuration)
        |- maven-settings.xml (to resolve dependencies if a user has no MAVEN_HOME)
        |- bin
        |  |- chowntree.sh
        |  `- service-wrapper.sh
        |- run
        |  |- locks       (holds all locking data)
        |  |- ports
        |  '- sleep.json  (optional, holds sleeping stages)
        |- downloads (caches Tomcat- and Service Wrapper downloads)
        |- extensions (for jars with Stool extensions)
        |- logs
        |  |- stool-YYmmDD.log(.gz)
        |  :
        |- system
        |  |- dashboard.properties (Dashboard configuration)
        |  |- dashboard (stage directory of the dashboard stage)
        |  :
        |- service-wrapper
        |  |- wrapper-linux-x86-64-x.y.z (installed service wrapper)
        |  :
        |- tomcat
        |  |- apache-tomcat-x.y.z (installed Tomcat)
        |  :
        |- shell.rc (to initialized the users interactive shell)
        |- bash.complete
        '- backstage
           |- id (symlink to a backstage directory
           :
      </programlisting>

... of stage directory

      <programlisting>
        :
        :  (normal project files)
        :
        '- .backstage
          |- config.json (stage properties)
          |- .m2 (Maven repository for this stage)
          |- buildstats.json
          |- ssl (generated certs)
          |- run (pid stuff for service wrapper)
          |- service
          |  |- service-wrapper.sh
          |  '- service-wrapper.conf
          '- tomcat (tomcat for this stage)
             |- conf (standard tomcat directory with generated server.xml)
             |- temp (standard tomcat directory)
             |- work (standard tomcat directory)
             '- logs
                '- applogs (application log files for pustefix apps)
      </programlisting>
