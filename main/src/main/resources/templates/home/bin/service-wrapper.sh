#! /bin/sh
# Java Service Wrapper (http://wrapper.tanukisoftware.com) script with some extra inital code

# Expected environment:
#  (cwd) stage directory
#  $1    catalina_home
#  $2    wrapper_home (${STOOL_HOME}/service-wrapper/${SERVICE_WRAPPER_NAME})
#  $3    backstage    (${BACKSTAGE})
#  (everything else goes to the original script)

CATALINA_HOME=$1 ; shift
WRAPPER_HOME=$1 ; shift
BACKSTAGE=$1 ; shift

export CATALINA_BASE=$BACKSTAGE/tomcat
export WRAPPER_CMD=${WRAPPER_HOME}/bin/wrapper
export WRAPPER_CONF=$BACKSTAGE/service/service-wrapper.conf
export PIDDIR=$BACKSTAGE/run

export CATALINA_HOME
export WRAPPER_HOME

$BACKSTAGE/service/service-wrapper.sh "$@"