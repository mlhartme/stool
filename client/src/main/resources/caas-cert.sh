#!/bin/sh
set -e

certname=$1
destdir=$2

if [ -d ${destdir} ] ; then
  if [ $(find "${destdir}" -mtime +60) ]; then
    echo "Removing certificate after 60 days: ${destdir}"
    rm -rf ${destdir}
  fi
fi
if [ -d ${destdir} ] ; then
  echo "re-using certificate: ${destdir}"
else
  echo "generating ${certname} -> ${destdir}"
  mkdir ${destdir}

  # length: 32 chars hash + <30 chars for display host name
  hashname=$(echo "${certname}" | md5sum | cut -f 1 -d ' ').${{domain}}

  echo "[ req ]" > ${destdir}/config.cnf
  echo "prompt = no" >> ${destdir}/config.cnf
  echo "distinguished_name = req_dn" >> ${destdir}/config.cnf
  echo "req_extensions = req_ext" >> ${destdir}/config.cnf
  echo >> ${destdir}/config.cnf
  echo "[ req_dn ]" >> ${destdir}/config.cnf
  echo "commonName = ${hashname}" >> ${destdir}/config.cnf
  echo >> ${destdir}/config.cnf
  echo "[ req_ext ]" >> ${destdir}/config.cnf
  echo "subjectAltName=@alt_names" >> ${destdir}/config.cnf
  echo >> ${destdir}/config.cnf
  echo "[ alt_names ]" >> ${destdir}/config.cnf
  echo "DNS.1=${certname}" >> ${destdir}/config.cnf
  echo "DNS.2=*.${certname}" >> ${destdir}/config.cnf

  openssl req -new -newkey rsa:2048 -nodes -config ${destdir}/config.cnf -keyout ${destdir}/key.pem -out ${destdir}/csr.pem >${destdir}/createreq.log 2>&1
  curl --silent --show-error -H "Content-Type: text/plain" --data-binary @${destdir}/csr.pem https://api-next.pki.1and1.org/cpstage -o ${destdir}/cert.pem
  curl --silent --show-error http://pub.pki.1and1.org/pukiautomatedissuingca2.crt -o ${destdir}/intermediate.pem
  cat ${destdir}/cert.pem ${destdir}/intermediate.pem > ${destdir}/chain.pem
  openssl pkcs12 -export -in ${destdir}/chain.pem -inkey ${destdir}/key.pem -out ${destdir}/keystore.p12 -name tomcat -passout pass:changeit
fi
