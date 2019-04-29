FROM contargo.server.lan/cisoops-public/java-8

# path on docker host
ENV SERVER_HOME=

ENV LOGLEVEL=INFO
ENV REGISTRY_NAMESPACE=main
ENV PORT_FIRST=8000
ENV PORT_LAST=9999
ENV BASE_MEMORY=400

# host name of the docker host
ENV HOSTNAME=walter.united.domain
ENV ADMIN=
ENV AUTO_REMOVE=-1
ENV LDAP_URL=
ENV LDAP_PRINCIPAL=
ENV LDAP_CREDENTIALS=
ENV LDAP_UNIT=
ENV MAIL_HOST=
ENV MAIL_USERNAME=
ENV MAIL_PASSWORD=
ENV QUOTA=0
ENV SECRETS=etc/fault/workspace
ENV CERTIFICATE=
ENV ENVIRONMENT=


ENV OPTS=

COPY maven/*.jar /usr/local/bin/stool-server.jar
RUN apt-get update && \
    apt-get install -y --no-install-recommends dnsutils

CMD java $OPTS -jar /usr/local/bin/stool-server.jar
