FROM debian:stretch-slim

# Default to UTF-8 file.encoding
ENV LANG C.UTF-8

ENV JAVA_VERSION 8u141
ENV JAVA_DEBIAN_VERSION 8u141-b15-1~deb9u1
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64/jre

RUN set -x; \
	if [ ! -d /usr/share/man/man1 ]; then \
		mkdir -p /usr/share/man/man1; \
	fi; \
	apt-get update && apt-get install -y --no-install-recommends ca-certificates curl wget procps bzip2 unzip xz-utils openjdk-8-jre="$JAVA_DEBIAN_VERSION" && rm -rf /var/lib/apt/lists/*

ENV CATALINA_HOME /backstage/tomcat
ENV PATH $CATALINA_HOME/bin:$PATH
WORKDIR $CATALINA_HOME

EXPOSE 9684
EXPOSE 9685
#CMD ["ls", "-la", "/backstage/tomcat/bin"]
CMD ["/backstage/tomcat/bin/catalina.sh", "run"]
