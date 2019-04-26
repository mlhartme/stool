FROM contargo.server.lan/cisoops-public/java-8

ENV STOOL_SERVER_HOME=/usr/local/stool
ENV OPTS=

COPY stool-server.jar /usr/local/bin/
RUN apt-get update && \
    apt-get install -y --no-install-recommends dnsutils && \
    java -jar /usr/local/bin/stool-server.jar setup -batch '{"hostname" : "localhost" }'

CMD java $OPTS -jar /usr/local/bin/stool-server.jar run
