#!/usr/bin/env bash

PROGRAMPATH=`dirname ${0}`

source ${PROGRAMPATH}/../../base.sh

ant -q -f ../../src/tools/mapgui/build.xml
java ${JFLAGS[@]} -cp ../../src/essentials/config${PATHSEP}${CORE_DIR}/java/lintim-core.jar${PATHSEP}../../src/essentials/sl-helper/PTNTools${PATHSEP}../../src/essentials/dm-helper/EAN${PATHSEP}../../src/essentials/dm-helper/Tools${PATHSEP}../../libs/Processing/core.jar${PATHSEP}../../libs/log4j/log4j-1.2.17.jar${PATHSEP}../../libs/Unfolding/Unfolding.jar${PATHSEP}../../libs/Processing/jogl-all.jar${PATHSEP}../../libs/Processing/gluegen-rt.jar${PATHSEP}../../libs/G4P/G4P.jar${PATHSEP}../../libs/Processing/gluegen-rt-natives-linux-amd64.jar${PATHSEP}../../libs/Processing/jogl-all-natives-linux-amd64.jar${PATHSEP}../../src/tools/mapgui MapGUI "basis/Config.cnf"
