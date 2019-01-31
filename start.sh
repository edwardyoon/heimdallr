#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

if [[ $1 != "standby" && $1 != "live" && $1 != "dev" ]]; then
  echo " - Heimdallr usage :"
  echo "   $ start.sh [live|standby|dev]"
  echo
  exit 0
fi

echo "Heimdallr Server Starting ..."
if [[ ! -d "./logs" ]]; then
  mkdir -p logs && echo "created dir:logs"
fi

# JVM Options
HEIMDALLR_DEFAULT="-server -Dname=heimdallr -Xms3027M -Xmx3072M -Xss8M"
HEIMDALLR_EXTENDS="-XX:MaxMetaspaceSize=512M -XX:+CMSClassUnloadingEnabled -XX:NewRatio=2 -XX:+UseConcMarkSweepGC -XX:MaxGCPauseMillis=850"

# Run Process
export JAVA_OPTS="$HEIMDALLR_DEFAULT $HEIMDALLR_EXTENDS $HEIMDALLR_MONIT"
echo && nohup sbt "run $1" 1> /dev/null 2> logs/heimdallr.error.log &
