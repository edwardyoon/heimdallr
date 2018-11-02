# Pull base image
FROM openjdk:8

# Env variables
ENV SCALA_VERSION 2.11.8
ENV SBT_VERSION 0.13.15

# Install Scala
RUN \
  curl -fsL https://downloads.typesafe.com/scala/$SCALA_VERSION/scala-$SCALA_VERSION.tgz | tar xfz - -C /root/ && \
  echo >> /root/.bashrc && \
  echo "export PATH=~/scala-$SCALA_VERSION/bin:$PATH" >> /root/.bashrc

# Install sbt
RUN \
  curl -L -o sbt-$SBT_VERSION.deb https://dl.bintray.com/sbt/debian/sbt-$SBT_VERSION.deb && \
  dpkg -i sbt-$SBT_VERSION.deb && \
  rm sbt-$SBT_VERSION.deb && \
  apt-get update && \
  apt-get install sbt

# Setup Heimdallr
WORKDIR /Heimdallr
ADD . /Heimdallr
RUN sbt test

# Open port for Heimdallr-Server ( Default : 8080 )
EXPOSE 8080 8008 6379

# Running Heimdallr-Server
CMD sbt -mem 48000 run
