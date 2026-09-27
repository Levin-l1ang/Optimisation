#!/usr/bin/env bash

set -e

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../../../base.sh

bash ${PROGRAMPATH}/add_local_jars_to_mvn.sh

mvn package -f ${PROGRAMPATH}/pom.xml

if [[ $2 == algo ]]; then
    java "${JFLAGS[@]}" -Xmx24g -jar ${PROGRAMPATH}/target/robustness-ml-1.0-SNAPSHOT.jar $1 algo
elif [[ $2 == eval ]]; then
    java "${JFLAGS[@]}" -Xmx24g -jar ${PROGRAMPATH}/target/robustness-ml-1.0-SNAPSHOT.jar $1 eval
else
    echo "Error: Invalid call to run.sh of robustness_ml: $2"
    exit 1
fi

