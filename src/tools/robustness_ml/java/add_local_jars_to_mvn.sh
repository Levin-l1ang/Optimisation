#!/usr/bin/env bash

set -e

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../../../base.sh

if [[ -z "${GUROBI_HOME}" ]]; then
    echo "GUROBI_HOME environment variable not set. Please set it to point to gurobi so that we can find the corresponding jar"
fi

mvn install:install-file -Dfile="${GUROBI_HOME}/lib/gurobi.jar" -DgroupId=com.gurobi -DartifactId=gurobi -Dversion=9.1.1 -Dpackaging=jar -DgeneratePom=true
ant -f ${CORE_DIR}/java/build.xml
mvn install:install-file -Dfile=${CORE_DIR}/java/lintim-core.jar -DgroupId=net.lintim -DartifactId=java-core -Dversion=2020.12.1 -Dpackaging=jar -DgeneratePom=true
