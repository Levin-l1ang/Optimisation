#!/usr/bin/env bash

SRC_DIR="../../src"
animation_dir=$("${SRC_DIR}"/config/config_cmd -c basis/Config.cnf -s tim_cp_animate_directory -t string -u)

echo "Creating pictures..."

for i in "${animation_dir}"/pic*.dot; do
	dot -Tgif -o $i.gif $i
done

echo "done.\n"

export LANG="en_US.UTF-8"
gqview -h 1>/dev/null 2>&1
gqview -f -s ${animation_dir}

#gqview -f -s ${animation_dir} &
#PID=$!
#sleep 3s
#gqview --remote --delay=0.5 1>/dev/null 2>&1
#gqview --remote --slideshow-start 1>/dev/null 2>&1
#
#while [[ $(ps aux | awk '{print $2}' | grep ${PID}) -eq ${PID} ]]; do
#	sleep 1s
#done

rm -f "${animation_dir}"/*
