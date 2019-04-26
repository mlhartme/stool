#!/bin/sh
set -e

certname=$1
dest=$2

if [ -f $dest ] ; then
  echo "re-using certificate: $dest"
else
  echo "generating ${certname} -> ${dest}"
  openssl req -x509 -newkey rsa:2048 -keyout key.pem -out cert.pem -days 365 -nodes -subj /CN=${certname}
  openssl pkcs12 -export -in cert.pem -inkey key.pem -out ${dest} -name tomcat -passout pass:changeit
fi
