#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../../base.sh

# no default value specified in global config
# default_headway_value==`"${CONFIGCMD[@]}" -s ??? -u`
echo "$(date +'%Y-%m-%d %H:%M:%S'): INFO: Begin reading configuration"
default_headway_value=`"${CONFIGCMD[@]}" -s ptn_default_headway_value -u`

EDGES=`"${CONFIGCMD[@]}" -s default_edges_file -u`

# 1st argument = EDGE-file, 2nd Argument Output_file
function readprint {
   edges=()
   while IFS=';' read -r col1 col2 || [[ -n "$col1" ]];
   do
        # Skip empty lines
        if [[ -z "$col1" ]]; then
            continue
        fi
       	if [[ ${col1:0:1} != "#" ]] ;
   		then
   			no_duplicate=1
   			nbr=`echo ${#edges[@]}+1|bc`
   			for ((i=1; i<$nbr; i++));
   			do
   				if [  $[${edges[$i]} == $col1] == 1 ];
   					then no_duplicate=0;
   				fi
   			done;
   			if [ $no_duplicate == 1 ];
   				then 	echo "$col1;$default_headway_value" >> $2;
            			edges[$nbr]=$col1;
   			fi
   	fi
   done < $1
   #echo "${edges[*]}"
}
headways_header=`"${CONFIGCMD[@]}" -s headways_header -u`
OUTPUT=`"${CONFIGCMD[@]}" -s default_headways_file -u`
echo "$(date +'%Y-%m-%d %H:%M:%S'): INFO: Finished reading configuration"

echo "$(date +'%Y-%m-%d %H:%M:%S'): INFO: Begin writing headways"
echo "# headways initialized by create_headways.sh" > $OUTPUT
echo "# headways initialized with default value $default_headway_value" >> $OUTPUT
echo "#$headways_header" >> $OUTPUT


readprint "$EDGES" "$OUTPUT"
echo "$(date +'%Y-%m-%d %H:%M:%S'): INFO: Finished writing headways"
