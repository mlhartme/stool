FROM contargo.server.lan/cisoops-public/java-13:1.0.4
# TODO: use java 14 when https://bugs.openjdk.java.net/browse/JDK-8236039 is included

ENV LOGLEVEL=INFO
ENV PORT_FIRST=31000
ENV PORT_LAST=31999
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

CMD java $OPTS -jar /usr/local/bin/stool-server.jar
