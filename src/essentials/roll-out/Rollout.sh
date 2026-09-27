#!/usr/bin/env bash

PROGRAMPATH=`dirname ${0}`

source ${PROGRAMPATH}/../../base.sh

default_events_expanded_file=$("${CONFIGCMD[@]}" -s default_events_expanded_file -u)
default_timetable_expanded_file=$("${CONFIGCMD[@]}" -s default_timetable_expanded_file -u)

ant -q -f ${PROGRAMPATH}/build.xml build-rollout
java "${JFLAGS[@]}" -classpath ${CLASSPATH}${PATHSEP}${PROGRAMPATH}${PATHSEP}${PROGRAMPATH}/../config${PATHSEP}${PROGRAMPATH}/../dm-helper/Tools${PATHSEP}${PROGRAMPATH}/../dm-helper/EAN Rollout

awk -F ';' '{print $1 "; " $4}' "$default_events_expanded_file" > "$default_timetable_expanded_file"

