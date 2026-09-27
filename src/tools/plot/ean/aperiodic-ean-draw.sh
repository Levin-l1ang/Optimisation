#!/usr/bin/env bash
set -e

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../../../base.sh

default_ean_dot_file=`"${CONFIGCMD[@]}" -s filename_aperiodic_ean_dot_file -u`
default_ean_graph_file=`"${CONFIGCMD[@]}" -s filename_aperiodic_ean_graph_file -u`

ant -q -f ${PROGRAMPATH}/build.xml
java -Djava.util.logging.config.file=${PROGRAMPATH}/../../../core/java/logging.properties -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/build${PATHSEP}${PROGRAMPATH}/../../../core/java/lintim-core.jar net.lintim.main.draw.TransformAperiodicEanToDot $1

dot -Tpng ${default_ean_dot_file} -o ${default_ean_graph_file}