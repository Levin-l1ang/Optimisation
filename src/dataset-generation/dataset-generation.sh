#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../base.sh

DG_MODEL=`"${CONFIGCMD[@]}" -s dg_model -u`
PTN_NAME=`"${CONFIGCMD[@]}" -s ptn_name -u`


if [[ ${DG_MODEL,,} == "parametrized_city" ]]; then
    cp -R ${PROGRAMPATH}/../../datasets/template ${PROGRAMPATH}/../../datasets/${PTN_NAME}
    cp ${PROGRAMPATH}/../../datasets/dataset-generation/basis/Config.cnf ${PROGRAMPATH}/../../datasets/${PTN_NAME}/basis/Config.cnf
	bash ${PROGRAMPATH}/parametrized-city/run.sh $1
elif [[ ${DG_MODEL,,} == "ring" ]]; then
    cp -R ${PROGRAMPATH}/../../datasets/template ${PROGRAMPATH}/../../datasets/${PTN_NAME}
    cp ${PROGRAMPATH}/../../datasets/dataset-generation/basis/Config.cnf ${PROGRAMPATH}/../../datasets/${PTN_NAME}/basis/Config.cnf
	bash ${PROGRAMPATH}/ring/run.sh $1
elif [[ ${DG_MODEL,,} == "crop" ]]; then
    DATASET_TO_CROP=`"${CONFIGCMD[@]}" -s dg_dataset_to_crop -u`
    cp -R ${PROGRAMPATH}/../../datasets/${DATASET_TO_CROP} ${PROGRAMPATH}/../../datasets/${PTN_NAME}
    cp ${PROGRAMPATH}/../../datasets/dataset-generation/basis/Config.cnf ${PROGRAMPATH}/../../datasets/${PTN_NAME}/basis/After-Config.cnf
    # remove first to and last 3 lines with include statements to avoid infinity loops
    tail -n +3 ${PROGRAMPATH}/../../datasets/${PTN_NAME}/basis/After-Config.cnf | head -n -3 > tmp && mv tmp ${PROGRAMPATH}/../../datasets/${PTN_NAME}/basis/After-Config.cnf
    cd ${PROGRAMPATH}/../../datasets/${PTN_NAME}
    make clean
	bash ${PROGRAMPATH}/crop/run.sh $1
else
	echo "Error: Requested DG_MODEL \"${DG_MODEL}\" not available!"
	exit 1
fi

EXITSTATUS=$?

exit ${EXITSTATUS}
