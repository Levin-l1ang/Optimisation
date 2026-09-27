#!/usr/bin/env bash

set -e

PROGRAMPATH=`dirname $0`
PWD=`pwd`
start_index=`awk -v a="$PWD" -v b="datasets" 'BEGIN{print index(a,b)}'`
end_index=${#PWD}
filename=${PROGRAMPATH}/"run_matching.m"

cat > ${filename}<<EOF
inst_dir='${PWD}';
path(path,'../../../essentials/config');
config=strcat('../../../../',inst_dir(${start_index}:${end_index}),'/basis/Config.cnf');
Start_Here(config);
exit;
EOF

matlab -nodesktop -nosplash -minimize -r "run('${filename}')"
if [ -t 0 ]; then   # only run if stdin is a terminal
	stty sane
fi

