#!/usr/bin/env bash
set -e
PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../../base.sh

mvn_install_java_core

mvn -f ${PROGRAMPATH}/pom.xml package
java "${JFLAGS[@]}" -jar ${PROGRAMPATH}/target/complete-linepool-1.0-SNAPSHOT.jar basis/Config.cnf