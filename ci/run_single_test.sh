#!/usr/bin/env bash
set -e

# Try to source xpress file, if it exists
if [[ -f ~/xpress.sh ]]; then
    echo "Manually enabling Xpress"
    source /opt/xpressmp/bin/xpvars.sh
fi

if [[ -d /scratch/aschiewe/conda-envs/lintim ]]; then
    echo "Enabling conda lintim environment"
    source /opt/anaconda3/etc/profile.d/conda.sh
    conda activate /scratch/aschiewe/conda-envs/lintim
fi


cd "$(dirname "$0")"
# Now run every test in this directory
python3 util/test_framework.py $1
