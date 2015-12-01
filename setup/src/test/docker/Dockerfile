FROM ubuntu:15.04

MAINTAINER Michael Hartmeier <michael.hartmeier@1und1.de>

RUN apt-get -y update || echo failed
RUN apt-get install -y openjdk-8-jdk
COPY stool.deb /tmp/stool.deb
RUN dpkg -i /tmp/stool.deb
