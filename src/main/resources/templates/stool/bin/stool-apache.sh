#! /bin/bash
#@@CCK 2012-05-03
#
# takes two argument (action and httpd file to use)
#
# invocation only allowed from stool
#
# TODO: remove when duso is obsolete

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

# positional arguments
ACTION=$1; shift
HTTPD_CONF=$1; shift

function isotime() {
    date +'%Y-%m-%d %H:%M:%S'
}

function log() {
    # global LOGFILE
    local S
    S=$1; shift

    echo "$(isotime) [$(whoami)] $S" >> "$LOGFILE"
}

if [ -z "$ACTION" -o -z "$HTTPD_CONF" ]; then
    echo "$SCRIPT: two arguments required" 1>&2
    exit 1
fi


# check if catalina.sh is executable
if [ ! -f "$HTTPD_CONF" ]; then
    echo "$SCRIPT: $HTTPD_CONF: no such file" 1>&2
    exit 1
fi
OWNER=$(stat -c '%U' "$HTTPD_CONF")

if [ -z "$OWNER" ]; then
    echo "$SCRIPT: $HTTPD_CONF: cannot determine owner" 1>&2
    exit 1
fi

# check if logfile is writable
touch -a "$LOGFILE" > /dev/null 2>&1
if [ ! -w "$LOGFILE" ]; then
    echo "$SCRIPT: $LOGFILE: cannot write to logfile" 1>&2
    echo "exiting..." 1>&2
    exit 1
fi

sudo -u "$OWNER" /usr/sbin/apache2ctl -f "$HTTPD_CONF" -k "$ACTION"

RET=$?
if [ $RET -eq 0 ]; then
    log "SUCCESS: sudo [-u $OWNER] /usr/bin/apachectl -f $HTTPD_CONF -k $ACTION"
else
    log "FAILED: sudo [-u $OWNER] /usr/bin/apachectl -f $HTTPD_CONF -k $ACTION"
fi
exit $RET


