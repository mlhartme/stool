#! /bin/sh
# Arguments
#   newUser dir*

SCRIPT=$(basename $0)
NEWUSER=$1; shift

if [ -z "$NEWUSER" ]; then
    echo "$SCRIPT: missing newUser arguments" 1>&2
    exit 1
fi

if [[ $NEWUSER == *":"* ]]
then
  echo "$SCRIPT: colon is not allowed in newUser argument: $NEWUSER" 1>&2
  exit 1
fi

if [[ $NEWUSER == "root" ]]
then
  echo "$SCRIPT: chown to root is not allowed" 1>&2
  exit 1
fi

# check if user exists
if ! id "$NEWUSER" > /dev/null 2>&1; then
    echo "$SCRIPT: $NEWUSER: no such user" 1>&2
    exit 1
fi

while true ; do
    DIR=$1
    shift
    if [ -z $DIR ] ; then
        exit
    fi
    chown -R "$NEWUSER" "$DIR"
    RET=$?
    if [ ! $RET -eq 0 ]; then
        echo "FAILED: chown -R $NEWUSER $DIR"
        exit $RET
    fi
done
