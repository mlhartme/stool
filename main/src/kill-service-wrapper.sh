#! /bin/sh
#
# Sends kill -9 to the service wrapper of the specified stage
#
# usage
#   kill-service-wrapper.sh stageName

# CAUTION: currently unused
# TODO
# * locate LIB
# * Mac OS has different ps options

set -e
NAME=$1
if [ -z "$NAME" ]; then
  echo "missing stage name"
  exit 1
fi
shift

LIB=/opt/ui/opt/tools/stool
BACKSTAGE=$LIB/backstages/$NAME
if [ ! -d $BACKSTAGE ]; then
  echo backstage not found: $BACKSTAGE
  exit 1
fi

FILE=$BACKSTAGE/shared/run/tomcat.pid
PID=$(cat $FILE)

CMD=$(ps --no-headers --format args $PID)

case "$CMD" in
  $LIB/service-wrapper/wrapper-linux-x86-64-3.5.TODO/bin/wrapper*)
    echo "ok"
    ;;
  *)
    echo "unexpected command: $CMD"
    exit 1
    ;;
esac

kill -9 $PID
rm $FILE
echo "killed pid $PID"
