#!/bin/bash
set -e
set -x

certname=$1
dest=$2

echo "generating ${certname} -> ${dest}"

# length: 32 chars hash + <30 chars for display host name
hashname=$(echo "${certname}" | md5sum | cut -f 1 -d ' ').${{domain}}

echo "[ req ]" > config.cnf
echo "prompt = no" >> config.cnf
echo "distinguished_name = req_dn" >> config.cnf
echo "req_extensions = req_ext" >> config.cnf
echo >> config.cnf
echo "[ req_dn ]" >> config.cnf
echo "commonName = ${hashname}" >> config.cnf
echo >> config.cnf
echo "[ req_ext ]" >> config.cnf
echo "subjectAltName=@alt_names" >> config.cnf
echo >> config.cnf
echo "[ alt_names ]" >> config.cnf
echo "DNS.1=${certname}\n" >> config.cnf
echo "DNS.2=*.${certname}\n" >> config.cnf

openssl req -new -newkey rsa:2048 -nodes -config config.cnf -keyout key.pem -out csr.pem >createreq.log 2>&1
# TODO: -k because issuingca is not trused by the Stool container
curl -k --silent --show-error -H "Content-Type: text/plain" --data-binary @csr.pem https://api-next.pki.1and1.org/cpstage -o cert.pem
curl --silent --show-error http://pub.pki.1and1.org/pukiautomatedissuingca2.crt -o intermediate.pem
cat cert.pem intermediate.pem > chain.pem
openssl pkcs12 -export -in chain.pem -inkey key.pem -out ${dest} -name tomcat -passout pass:changeit
