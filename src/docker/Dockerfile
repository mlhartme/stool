FROM contargo.server.lan/cisoops-public/java-14:1.2.0

ENV HELM_EXPERIMENTAL_OCI=1
ENV SC_HOME=/usr/local/stool
ENV SC_OPTS=

RUN curl -o /tmp/helm.tgz https://get.helm.sh/helm-v3.5.0-linux-amd64.tar.gz && \
    cd /usr/local/bin && \
    tar zxf /tmp/helm.tgz --strip-components=1 linux-amd64/helm && \
    apt-get update && apt-get install -y --no-install-recommends git
COPY maven/sc /usr/local/bin/sc
RUN sc setup

CMD [ "sc", "server" ]
