#!/bin/bash
heimdallr_pid=$(\ps -ef | grep java | grep "heimdallr" | grep -v "grep" | awk '{print $2}')
if [[ $heimdallr_pid = "" ]]; then
	echo "no pid" && echo
	exit 0
fi

echo "Heimdallr Server Stopped, PID: $heimdallr_pid"
echo && kill -s TERM $heimdallr_pid

