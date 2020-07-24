FROM contargo.server.lan/cisoops-public/java-14:1.0.2

ENV LOGLEVEL=INFO
ENV HOST=host.docker.internal
ENV ADMIN=
ENV AUTO_REMOVE=-1
ENV LDAP_URL=
ENV LDAP_PRINCIPAL=
ENV LDAP_CREDENTIALS=
ENV LDAP_UNIT=
ENV LDAP_SSO=
ENV MAIL_HOST=
ENV MAIL_USERNAME=
ENV MAIL_PASSWORD=
ENV MEMORY_QUOTA=0
ENV DISK_QUOTA=0
ENV CERTIFICATE=
ENV APP_PROPERTIES_FILE=WEB-INF/classes/META-INF/pominfo.properties
ENV APP_PROPERTIES_PREFIX=stool.
ENV JMX_USAGE="jconsole localhost:%s"
ENV ENVIRONMENT=

ENV OPTS=

COPY maven/*.jar /usr/local/bin/stool-server.jar
COPY cert.sh /var/lib/stool/cert.sh
RUN mkdir /var/lib/stool/certs

CMD [ "sh", "-c", "exec java ${OPTS} -jar /usr/local/bin/stool-server.jar" ]
