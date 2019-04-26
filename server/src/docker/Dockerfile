FROM contargo.server.lan/cisoops-public/java-8

ENV STOOL_SERVER_HOME=/usr/local/stool
ENV OPTS=

COPY maven/*.jar /usr/local/bin/stool-server.jar
RUN apt-get update && \
    apt-get install -y --no-install-recommends dnsutils

CMD java $OPTS -jar /usr/local/bin/stool-server.jar
