FROM contargo.server.lan/cisoops-public/java-14:1.0.2

ENV LOGLEVEL=INFO
ENV FQDN=host.docker.internal
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
ENV CERTIFICATE=
ENV SC_HOME=/usr/local/sc
ENV SC_OPTS=

RUN curl -o /tmp/helm.tgz https://get.helm.sh/helm-v3.3.4-linux-amd64.tar.gz && \
    cd /usr/local/bin && \
    tar zxf /tmp/helm.tgz --strip-components=1 linux-amd64/helm && \
    apt-get update && apt-get install -y --no-install-recommends git
COPY maven/sc /usr/local/bin/sc
RUN sc setup

CMD [ "sc", "server" ]
