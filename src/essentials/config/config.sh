#!/usr/bin/env bash

DIRNAME=`dirname "$0"`;
COMMAND="${DIRNAME}"/config_cmd;

make -C "${DIRNAME}" config_cmd > /dev/null &&
"${COMMAND}" $@ ||
exit 1