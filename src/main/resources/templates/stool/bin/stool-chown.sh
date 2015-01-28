#! /bin/bash
#@@CCK 2012-04-23
#
# Takes the current working directory
#    cwd       stool home
# and two arguments
#    stage-name
#    username
# to chown stage-dir to new user
#
# Invocation only allowed from stool.
#
# example: chown.sh stage3 heinz
#

SCRIPT=$(basename $0)
SCRIPTDIR=$(cd $(dirname $0) && pwd)

LOGFILE="$SCRIPTDIR/$SCRIPT.log"
VALIDPARENT='stool'

# this is no security, but just a hint for correct caller
if ! ps h -o comm,args -p $PPID | grep -q "$VALIDPARENT"; then
    echo "$SCRIPT: invocation only allowed from $VALIDPARENT" 1>&2
    pstree -pau 1>&2
    exit 1
fi

STOOL_HOME=`pwd`
if [ ! -d "$STOOL_HOME" ]; then
    echo "$SCRIPT: $STOOL_HOME: invalid working directory" 1>&2
    exit 1
fi

# positional arguments
STAGE=$1; shift
NEWUSER=$1; shift

function isotime() {
    date +'%Y-%m-%d %H:%M:%S'
}

function log() {
    # global LOGFILE
    local S
    S=$1; shift

    echo "$(isotime) [$(whoami)] $S" >> "$LOGFILE"
}

if [ -z "$STAGE" -o -z "$NEWUSER" ]; then
    echo "$SCRIPT: two arguments required" 1>&2
    exit 1
fi

# STAGE must not contain slash
if echo "$STAGE" | grep -q '/'; then
    echo "$SCRIPT: $STAGE: illegal stage-name" 1>&2
    exit 1
fi

WRAPPER="$STOOL_HOME/wrappers/$STAGE"

# check if stage-dir exists
if [ ! -d "$WRAPPER" ]; then
    echo "$SCRIPT: $WRAPPER: no such stage-dir" 1>&2
    exit 1
fi

# check if user exists
if ! id "$NEWUSER" > /dev/null 2>&1; then
    echo "$SCRIPT: $NEWUSER: no such user" 1>&2
    exit 1
fi

# check if logfile is writable
touch -a "$LOGFILE" > /dev/null 2>&1
if [ ! -w "$LOGFILE" ]; then
    echo "$SCRIPT: $LOGFILE: cannot write to logfile" 1>&2
    echo "exiting..." 1>&2
    exit 1
fi

ANCHOR=$(ls -ld "$WRAPPER/anchor")
DIRECTORY=$(expr "$ANCHOR" : '.*-> \(.*\)$')
chown -R "$NEWUSER" "$WRAPPER" "$DIRECTORY"

RET=$?
if [ $RET -eq 0 ]; then
    log "SUCCESS: chown -R $NEWUSER $WRAPPER $DIRECTORY"
else
    log "FAILED: chown -R $NEWUSER $WRAPPER $DIRECTORY"
fi
exit $RET

