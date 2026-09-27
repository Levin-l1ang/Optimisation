#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../base.sh

echo "$(date +'%Y-%m-%d %H:%M:%S'): INFO: Begin reading configuration"
header=`"${CONFIGCMD[@]}" -s depot_header -u`
output_file=`"${CONFIGCMD[@]}" -s filename_depot_file -u`
depot_index_list=`"${CONFIGCMD[@]}" -s vs_depot_index -u`
stop_file="basis/Stop.giv"
vehicle_number=`"${CONFIGCMD[@]}" -s vs_vehicle_number_upper_bound -u`

# Check, if values were found
if [[ -z "$output_file" || -z "$header" || -z "$depot_index_list" || -z "$vehicle_number" ]]; then
  echo "Error: Config values are missing!"
  exit 2
fi
echo "$(date +'%Y-%m-%d %H:%M:%S'): INFO: Finished reading configuration"


echo "$(date +'%Y-%m-%d %H:%M:%S'): INFO: Begin reading input data"
# Create Empty Depot File with Header
echo "# $header" > "$output_file"

# Create Associative Array(bash 4+)
declare -A stops_x
declare -A stops_y

# Read in Stop File, skip comment lines
while IFS=';' read -r stop_id short_name long_name x y; do
  # remove blanks
  stop_id=$(echo $stop_id | xargs)
  x=$(echo $x | xargs)
  y=$(echo $y | xargs)
  if [[ $stop_id =~ ^[0-9]+$ ]]; then
    stops_x[$stop_id]=$x
    stops_y[$stop_id]=$y
  fi
done < <(grep -v '^#' "$stop_file")
echo "$(date +'%Y-%m-%d %H:%M:%S'): INFO: Finished reading input data"


echo "$(date +'%Y-%m-%d %H:%M:%S'): INFO: Begin writing output data"
# For every depot index add stop information to depot file
IFS=',' read -ra indices <<< "$(echo "$depot_index_list" | tr -d '[]')"
for idx in "${indices[@]}"; do
  idx=$(echo $idx | xargs)  # remove blanks
  if [[ "$idx" == "-1" ]]; then
  # skip for this index
  continue
  fi
  if [[ -n "${stops_x[$idx]}" ]]; then
    echo "$idx; ${stops_x[$idx]}; ${stops_y[$idx]}; $vehicle_number" >> "$output_file"
  else
    echo "Warning: Did not find stop with ID $idx"
  fi
done
echo "$(date +'%Y-%m-%d %H:%M:%S'): INFO: Finished writing output data"



