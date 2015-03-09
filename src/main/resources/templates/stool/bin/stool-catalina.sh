#! /bin/bash
#@@CCK 2012-05-03
#
# Takes the current working directory
#    pwd        stage to be started
# and one arguments:
#    action     start, stop, run
# and the following environment variables
#    CATALINA_BASE
#    CATALINA_HOME
#    CATALINA_OPTS
#    CATALINA_PID
# to start/stop tomcat. Catalina processes are started in the current working directory.
#

SCRIPT=$(basename $0)

ACTION=$1; shift
if [ -z "$ACTION" ]; then
    echo "$SCRIPT: one argument required" 1>&2
    exit 1
fi

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
export STAGE=`basename $STAGE`

if [ ! -d "$CATALINA_HOME" ]; then
    echo "$SCRIPT: $CATALINA_HOME: catalina home not found" 1>&2
    exit 1
fi
if [ ! -d "$CATALINA_BASE" ]; then
    echo "$SCRIPT: $CATALINA_BASE: catalina base not found" 1>&2
    exit 1
fi
CATALINA_RUN="${{stool.home}}/bin/service-wrapper.sh"
if [ ! -x "$CATALINA_RUN" ]; then
    echo "$SCRIPT: $CATALINA_RUN: no such file or not executable" 1>&2
    exit 1
fi

case "$ACTION" in
    'run')
        sudo -u "$OWNER" "$CATALINA_RUN" "$ACTION"
    ;;
    'start')
        # this directory has to be deleted because it might contain out-dated configution.
        # It is is created at startup with owner permissions. When re-starting, the owner 
        # might have changed, so we delete it here with root permissions to avoid permission 
        # problems.
        rm -rf "${CATALINA_BASE}/conf/Catalina"
        sudo -u "$OWNER" "$CATALINA_RUN" "$ACTION"
    ;;
    'stop')
        sudo -u "$OWNER" "$CATALINA_RUN" "$ACTION" -force
    ;;
    *)
        echo "$SCRIPT: $ACTION: no such action" 1>&2
        exit 1
    ;;
esac

RET=$?
if [ ! $RET -eq 0 ]; then
    log "FAILED: sudo [-u $OWNER] $CATALINA_RUN $ACTION"
fi
exit $RET
