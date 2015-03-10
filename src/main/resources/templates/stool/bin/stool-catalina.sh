#! /bin/bash
#
# Argument: same as service-wrapper.sh

SCRIPT=$(basename $0)

STAGE=`pwd`
if [ ! -d "$STAGE" ]; then
    echo "$SCRIPT: $STAGE: no such directory" 1>&2
    exit 1
fi
OWNER=$(stat -c '%U' "$STAGE")
if [ -z "$OWNER" ]; then
    echo "$SCRIPT: $STAGE: cannot determine owner" 1>&2
    exit 1
fi

CATALINA_RUN="${{stool.home}}/bin/service-wrapper.sh"
if [ ! -x "$CATALINA_RUN" ]; then
    echo "$SCRIPT: $CATALINA_RUN: no such file or not executable" 1>&2
    exit 1
fi

sudo -u "$OWNER" "$CATALINA_RUN" "$@"
RET=$?
if [ ! $RET -eq 0 ]; then
    echo "FAILED: sudo [-u $OWNER] $@"
fi
exit $RET
