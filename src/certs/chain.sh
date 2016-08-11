#!/bin/sh
set -e
HOSTNAME=$1
API="https://api-next.pki.1and1.org/host"
openssl req -new -newkey rsa:2048 -nodes -subj /CN=${HOSTNAME} -keyout key.pem -out csr.pem

echo host intermediate
curl -f -k -H "Content-Type: text/plain" --data-binary @csr.pem ${API} -o cert.pem 

echo intermediate cert
curl http://pub.pki.1and1.org/pukiautomatedissuingca1.crt -o inter1.pem
curl http://pub.pki.1and1.org/pukirootca1.crt -o root.pem
cat inter1.pem root.pem >intermediate.pem

echo tomcat.p12
openssl pkcs12 -export -chain -in cert.pem -inkey key.pem -out tomcat.p12 -name tomcat -CAfile intermediate.pem -passout pass:changeit

echo tomcat.jks
keytool -importkeystore -srckeystore tomcat.p12 -srcstoretype pkcs12 -destkeystore tomcat.jks -deststoretype jks -deststorepass changeit -srcstorepass changeit
rm tomcat.p12
echo done

