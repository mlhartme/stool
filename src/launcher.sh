#!/bin/sh
APP="$0"
while [ -h "$APP" ] ; do
  ls=$(ls -ld "$APP")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then
    APP="$link"
  else
    APP=$(dirname "$APP")"/$link"
  fi
done
NAME=$(basename "$APP")
APP=$(dirname "$APP")
APP=$(cd "$APP" && pwd)
APP="$APP/$NAME"

exec java -Djava.awt.headless=true --add-exports java.naming/com.sun.jndi.ldap=ALL-UNNAMED $SC_OPTS -jar "$APP" "$@"
