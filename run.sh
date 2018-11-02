#!/bin/bash
DOCKER_IMAGE="heimdallr:v"$1
docker run -it -p 8080:8080 $DOCKER_IMAGE
