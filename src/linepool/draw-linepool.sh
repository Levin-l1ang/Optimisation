#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../base.sh

CLASSPATH="../../libs/jgrapht/jgrapht-core-1.5.0.jar:${CLASSPATH}"

ant -q -f ${PROGRAMPATH}/build.xml build-linepool
java "${JFLAGS[@]}" -cp ${CLASSPATH}${PATHSEP}${CORE_DIR}/java/lintim-core.jar${PATHSEP}${PROGRAMPATH}/../essentials/lp-helper${PATHSEP}${PROGRAMPATH}/build DrawLinepool "$@"

if [[ $2 == "lc" ]]; then
    DOTFILE=`"${CONFIGCMD[@]}" -s default_line_graph_file -u`
elif [[ $2 == "lpool" ]]; then
    DOTFILE=`"${CONFIGCMD[@]}" -s default_pool_graph_file -u`
elif [[ $2 == "rc" ]]; then
    DOTFILE=`"${CONFIGCMD[@]}" -s filename_rc_graph -u`
elif [[ $2 == "rpool" ]]; then
    DOTFILE=`"${CONFIGCMD[@]}" -s filename_rpool_graph -u`
fi
PNGFILE="${DOTFILE%.dot}.png"
PSFILE="${DOTFILE%.dot}.ps"
neato -n -Tpng $DOTFILE -o $PNGFILE
neato -n -Tps $DOTFILE -o $PSFILE


EXITSTATUS=$?

exit ${EXITSTATUS}
