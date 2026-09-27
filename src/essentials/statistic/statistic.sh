#!/usr/bin/env bash

DIRNAME=`dirname $0`;
COMMAND=${DIRNAME}/statistic_cmd;

make -C ${DIRNAME} statistic_cmd > /dev/null &&
${COMMAND} $@ ||
exit 1
