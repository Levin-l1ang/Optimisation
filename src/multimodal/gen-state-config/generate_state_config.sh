#!/usr/bin/env bash

# use absolute path
PROGRAMPATH=$(cd $(dirname $0); pwd)

source ${PROGRAMPATH}/../../base.sh
filename_state_config=`"${CONFIGCMD[@]}" -s filename_state_config -u`
# state config must be removed before reading / modifying the config
rm -f $filename_state_config
# use absolute path instead of relative in pythonpath
PYTHON_CORE_PATH=$(cd $PROGRAMPATH/../../core/python; pwd)
PYTHONPATH=$PYTHONPATH:$PYTHON_CORE_PATH \
python3 $PROGRAMPATH/generate_state_config.py ${@:1}
