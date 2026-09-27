#!/usr/bin/env bash
set -e # exit if an error occurs during the execution of this script

# directory with source code, relative to from where this script is called
SRC_DIR="../../src"



# check whether config command line interface can be accessed
config="${SRC_DIR}/essentials/config/config_cmd"
if [[ ! -f "${config}" ]]; then
	echo "${config} does not exist" >&2
	exit 1
fi
if [[ ! -x "${config}" ]]; then
	echo "cannot execute ${config}" >&2
	exit 1
fi



# test whether directory with plots exists
animation_dir=$("${config}" -c basis/Config.cnf -s dm_animation_delays_output_dir -t string -u)
animation_nr=$("${config}" -c basis/Config.cnf -s dm_animation_delays_number_of_steps -t string -u)
animation_file=$("${config}" -c basis/Config.cnf -s default_delay_graph_file -t string -u)
gif_file=$("${config}" -c basis/Config.cnf -s filename_delay_animation_file -t string -u)

if [[ -z "${animation_dir}" || ! -d "${animation_dir}" ]]; then
	echo "${animation_dir} does not exist" >&2
	exit 1
fi

if [[ "${animation_nr}" == "1" ]]; then
	neato -n -Tgif "${animation_file}" -o "${gif_file}"
    echo "Animation saved as ${gif_file}"
else

	# convert .dot files in .gif and save them temporarily
	for file in "${animation_dir}"/*.dot; do
		neato -n -Tgif "${file}" -o "${file}".gif
	done

	# create animation from .gif files
	convert -delay 10 "${animation_dir}"/*.gif "${gif_file}"
    current_datetime=$(date +'%Y-%m-%d %H:%M:%S')
    echo "${current_datetime}: INFO: Animation saved as ${gif_file}"

	# delete temporary .gif and .dot files
	rm -rf "${animation_dir}"

	# show GIF-animation
	animate "${gif_file}"
fi






