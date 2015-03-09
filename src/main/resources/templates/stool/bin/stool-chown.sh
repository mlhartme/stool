#! /bin/bash
#@@CCK 2012-04-23
#
# Takes the current working directory
#    cwd       stool home
# and two arguments
#    stage-name
#    username
# to chown stage-dir to new user

SCRIPT=$(basename $0)

STOOL_HOME=`pwd`
if [ ! -d "$STOOL_HOME" ]; then
    echo "$SCRIPT: $STOOL_HOME: invalid working directory" 1>&2
    exit 1
fi

# positional arguments
STAGE=$1; shift
NEWUSER=$1; shift

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

ANCHOR=$(ls -ld "$WRAPPER/anchor")
DIRECTORY=$(expr "$ANCHOR" : '.*-> \(.*\)$')
chown -R "$NEWUSER" "$WRAPPER" "$DIRECTORY"

RET=$?
if [ ! $RET -eq 0 ]; then
    echo "FAILED: chown -R $NEWUSER $WRAPPER $DIRECTORY"
fi
exit $RET

