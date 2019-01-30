#!/bin/bash
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
if [[ $1 = "live" ]]; then
	HEIMDALLR_MONIT="-javaagent:/home/ubuntu/spooncast-server-akkachat/utils/whatap/whatap.agent.tracer-1.6.3.jar"
fi

# Run Process
export JAVA_OPTS="$HEIMDALLR_DEFAULT $HEIMDALLR_EXTENDS $HEIMDALLR_MONIT"
echo && nohup sbt "run $1" 1> /dev/null 2> logs/heimdallr.error.log &
