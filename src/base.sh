#!/usr/bin/env bash

set -e

MAC_READLINK_COMMAND=greadlink
GNU_READLINK_COMMAND=readlink

if ! command -v $MAC_READLINK_COMMAND &> /dev/null
then
	READLINK_COMMAND=$GNU_READLINK_COMMAND
else
	READLINK_COMMAND=$MAC_READLINK_COMMAND
fi

declare -r SRC_DIR=`dirname "$(${READLINK_COMMAND} -f ${BASH_SOURCE[0]})"`

CORE_DIR="${SRC_DIR}/core"
LIB_DIR="${SRC_DIR}/../libs"


PATHSEP=":"
if [[ $OSTYPE == "cygwin" ]] ; then
	PATHSEP=";"
fi

CONFIGPROG="${SRC_DIR}/essentials/config/config.sh"
# First, check if the shell script is available and executable
if [[ ! -x ${CONFIGPROG} ]]; then
	echo "Error: the config command is not usable. Make sure that "
	echo ${CONFIGPROG}
	echo "exists and is executable."
	exit 1;
fi
if [[ ${#} -ge 1 ]]; then
	if [[ -r ${1} ]]; then
		CONFIGCMD=("${CONFIGPROG}" -c "${1}") #"${CONFIGPROG} -c ${1}"
	else
		echo "Error: The first argument should be a configuration file, "\
		"but the file \"${1}\" either does not exist or is not readable."
		exit 1
	fi
else
	CONFIGCMD=("${CONFIGPROG}" -c "basis/Config.cnf")
	if [[ ! -e "basis/Config.cnf" ]]; then
		echo "Error: no configuration file found. Make sure that "\
		"basis/Config.cnf exists."
		exit 1;
	fi
fi

generic_64_bit_only=`"${CONFIGCMD[@]}" -s generic_64_bit_only -u`

if [ "${generic_64_bit_only}" = "true" ] && [ ! `uname -m` = "x86_64" ] && [ ! `uname -m` = "arm64" ]; then
	echo -n "Error: your system is 32 bit. Either get yourself a 64 bit "
	echo    "capable computer or set generic_64_bit_only to false."
	exit 1
fi

JFLAGS=(-XX:+UseParallelGC -Djava.util.logging.config.file="${CORE_DIR}/java/logging.properties")

function mvn_install_java_core () {
	ant -f ${CORE_DIR}/java/build.xml
	mvn install:install-file -Dfile=${CORE_DIR}/java/lintim-core.jar -DgroupId=net.lintim -DartifactId=java-core -Dversion=2020.12.1 -Dpackaging=jar -DgeneratePom=true
}
