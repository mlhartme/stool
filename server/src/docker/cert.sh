#!/bin/sh
set -e

certname=$1
destdir=$2
fqdn=$3

if [ -d ${destdir} ] ; then
  echo "re-using certificate in: ${destdir}"
else
  # files in destdir
  #    key.pem
  #    cert.pem
  #    chain.pem
  #    keystore.p12
  echo "generating ${certname} -> ${destdir}"
  mkdir ${destdir}
  openssl req -x509 -newkey rsa:2048 -keyout ${destdir}/key.pem -out ${destdir}/cert.pem -days 365 -nodes -subj /CN=${certname}
  cp ${destdir}/cert.pem ${destdir}/chain.pem
  openssl pkcs12 -export -in ${destdir}/cert.pem -inkey ${destdir}/key.pem -out ${destdir}/keystore.p12 -name tomcat -passout pass:changeit
fi
