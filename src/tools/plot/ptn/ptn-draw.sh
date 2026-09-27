#!/usr/bin/env bash
set -e

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../../../base.sh

if [[ $2 == "zone" ]]; then
    filename_ptn_graph=`"${CONFIGCMD[@]}" -s filename_tariff_ptn_zone_graph -u`
elif [[ $2 == "ptn" ]]; then
    filename_ptn_graph=`"${CONFIGCMD[@]}" -s default_ptn_graph_file -u`
fi

ant -q -f ${PROGRAMPATH}/build.xml
java -Djava.util.logging.config.file=${CORE_DIR}/java/logging.properties -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/build${PATHSEP}${CORE_DIR}/java/lintim-core.jar net.lintim.util.draw.TransformPtnToDot basis/Config.cnf $2
filename="${filename_ptn_graph%.*}.png"
neato -n -Tpng ${filename_ptn_graph} -o ${filename}
