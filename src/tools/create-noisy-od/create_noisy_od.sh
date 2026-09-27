#!/usr/bin/env bash

echo This script is for reference purposes only and will terminate now. You
echo will find the OD pool in the basis/OD_Pool directory.
exit 1

LC_NUMERIC=en.US.UTF-8
iteration=1
timeout_seconds=20

timeout="timeout -2 ${timeout_seconds}"

statistic_filename=basis/OD_Pool/noisy_od.eva
od_pool_directory=basis/OD_Pool/

append_statistic(){
	echo $1 >> ${statistic_filename}
}

get_from_statistic(){
	echo `sh ../../src/essentials/statistic/statistic.sh -c statistic/statistic.sta -s $1 -u`
}

javatools_run(){
	sh ../../src/essentials/javatools/runner.sh ${1} basis/Config.cnf
}

echo "# line_concept;iteration;noise_level;lc_cost;overall_sum" > ${statistic_filename}

ant -q -f ../../src/essentials/javatools/build.xml

for noise_level in `seq 0.2 0.2 1`; do
	for iteration in `seq 1 5`; do
		cp basis/Load.giv.nominal basis/Load.giv
		cp basis/OD.giv.nominal basis/OD.giv
		echo "iteration; ${iteration}" > basis/After-Config.cnf
		echo "od_noise_level; ${noise_level}" >> basis/After-Config.cnf
		echo "load_generator_model; \"LOAD_FROM_PTN\"" >> basis/After-Config.cnf
		cat basis/After-Config.cnf
		javatools_run RandomizeOriginDestinationMatrix
		javatools_run RegenerateLoad
		overall_sum=`get_from_statistic od_overall_sum`
		noise_string=`printf "%03.0f" \`echo ${noise_level}*100 | bc\``
		suffix="_${noise_string}_${iteration}.giv"
		cp basis/OD.giv basis/OD_Pool/OD${suffix}
		cp basis/Load.giv basis/OD_Pool/Load${suffix}
		if timeout 300 make line-concept; then
			make eval-lines
			lc_cost=`get_from_statistic lc_cost`
			make ptn2ean
			append_statistic "success;${iteration};${noise_level};${lc_cost};${overall_sum}"
		else
			append_statistic "fail;${iteration};${noise_level};0;0;0"
		fi
	done
done
