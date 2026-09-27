#! /bin/sh

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../../base.sh
# Executes it

ant -q -f ${PROGRAMPATH}/build.xml
# Change to the folder bin

# Execute the program
java ${JFLAGS[@]} -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/build${PATHSEP}${PROGRAMPATH}/../../core/java/lintim-core.jar net.lintim.main.vehiclescheduling.MakeVehicleSchedule $1
