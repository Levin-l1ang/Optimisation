# TODO: Adjust DIR_TO_SRC to locate your source files
DIR_TO_SRC=../../src/vs-exercise
FILENAME_CONFIG=basis/Config.cnf
# TODO: Adjust CORE_PATH to locate the java core folder
CORE_PATH=../../src/core/java
JFLAGS=(-server -Xbatch -XX:+AggressiveHeap -Djava.util.logging.config.file="${CORE_PATH}/logging.properties")

practical-course-vs-vehicle-schedule:
	ant -q -f ${DIR_TO_SRC}/build.xml
	java "${JFLAGS[@]}" -classpath ${DIR_TO_SRC}/build:${CORE_PATH}/lintim-core.jar net.lintim.main.vehiclescheduling.SimpleVehicleScheduleMain basis/Config.cnf
