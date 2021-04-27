FROM contargo.server.lan/cisoops-public/java-16:1.2.5

ENV SC_HOME=/usr/local/stool
ENV SC_OPTS=

RUN cd /usr/local/bin && \
    curl https://get.helm.sh/helm-v3.5.0-linux-amd64.tar.gz | tar zx --strip-components=1 linux-amd64/helm && \
    apt-get update && apt-get install -y --no-install-recommends git && \
    apt-get clean
COPY maven/sc /usr/local/bin/sc
RUN sc setup

CMD [ "sc", "server" ]
