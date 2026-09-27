#!/usr/bin/env bash

# directory with source code, relative to from where this script is called
SRC_DIR=../../src



# check whether config command line interface can be accessed
config="${SRC_DIR}"/essentials/config/config_cmd
if [[ ! -f "${config}" ]]; then
	echo "${config} does not exist" >&2
	exit 1
fi
if [[ ! -x "${config}" ]]; then
	echo "cannot execute ${config}" >&2
	exit 1
fi

SOLVER=$("${config}" -c basis/Config.cnf -s DM_solver -t string -u)
echo "using solver ${SOLVER} - you can change the solver by setting the variable DM_solver"
if ! diff -q "${SRC_DIR}"/delay-management/ip-based/"${SOLVER^^}"/Solve.java "${SRC_DIR}"/delay-management/ip-based/Solve/Solve.java 1>/dev/null; then
	cp "${SRC_DIR}"/delay-management/ip-based/"${SOLVER^^}"/Solve.java "${SRC_DIR}"/delay-management/ip-based/Solve/Solve.java
	rm -f "${SRC_DIR}"/delay-management/ip-based/Solve/Solve.class
	rm -f "${SRC_DIR}"/delay-management/ip-based/Solve/DM.class
fi
