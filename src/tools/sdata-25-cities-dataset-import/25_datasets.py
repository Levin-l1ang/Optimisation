import ast
import csv
from datetime import datetime, UTC
import math
import networkx as nx
import numpy as np
import os
import pandas as pd
import pyproj
import logging
import shutil
from sklearn.cluster import AgglomerativeClustering
import subprocess
import sys
from preexisting_files import *
from core.io.config import ConfigReader
from core.io.lines import LineReader, LineWriter
from core.io.periodic_ean import PeriodicEANWriter
from core.io.ptn import PTNReader, PTNWriter
from core.model.eventType import EventType
from core.model.graph import Graph
from core.model.lines import LinePool, Line
from core.model.periodic_ean import PeriodicEvent, PeriodicActivity, EventType, LineDirection, ActivityType
from core.model.ptn import Stop, Link
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.util.config import Config

# A helper for running indices
class Counter:
    def __init__(self, first_value: int = 0):
        self.value = first_value

    def __call__(self) -> int:
        self.value += 1
        return self.value


def initialization_process(dataset_zip_file: str) -> None:
    """
    This procedure should be called when the folder this script is called from has no basis folder
    From there, this function creates the typical LinTim Layout which includes
    - The folder for all planning steps of the public transportation planning process
     (including the basis folder)
    - The files "Config.cnf","After-Config.cnf", "State-Config.cnf" as well as a Private Config file
      "Private-Config.cnf"
     is initialized with the default config parameters, for quicker control of the conversion process

    :param dataset_zip_file: name of zip-file the dataset which is to be assimilated into LinTim;
                            should have the ending ".zip"
    :type dataset_zip_file: str
    """
    logging.info("Begin unzipping the dataset folder")
    dataset_name = dataset_zip_file[:-4]
    cwd = os.getcwd()
    shutil.unpack_archive(filename=dataset_zip_file)
    logging.debug(cwd + "/" + dataset_name)
    logging.debug(cwd)
    
    file_names = os.listdir(cwd + "/" + dataset_name)  
    for file_name in file_names:
        shutil.move(os.path.join(cwd + "/" + dataset_name, file_name), cwd)
        
    os.rmdir(cwd + "/" + dataset_name)
    logging.info("Finished unzipping the dataset folder")

    logging.info("Begin creating folders for the planning processes")
    os.mkdir("./basis")
    os.mkdir("./line-planning")
    os.mkdir("./delay-management")
    os.mkdir("./graphics")
    os.mkdir("./statistic")
    os.mkdir("./tariff")
    os.mkdir("./timetabling")
    os.mkdir("./vehicle-scheduling")
    os.mkdir("./Debug")
    os.mkdir("./Debug/basis")
    os.mkdir("./Debug/line-planning")
    logging.info("Finished creating folders for the planning processes")

    logging.info("Begin creating Config files")
    # Config string is split into two to get the multimodal params in the right spot
    config_strs = get_config_str(dataset_name) 
    private_config_str = get_private_config_str()
    config_dict = {"Config.cnf": config_strs[0],
                   "Private-Config.cnf": private_config_str}
    for config_file_name, str_to_write in config_dict.items():
        logging.debug("Begin writing {}".format(config_file_name))
        with open("./basis/{}".format(config_file_name), "w") as text_file:
            text_file.write(str_to_write)
        logging.debug("Finished writing {}".format(config_file_name))
    mode_dict = {"tram": 0, "subway": 1, "rail": 2, "bus": 3, "ferry": 4, "cablecar": 5, "gondola": 6, "funicular": 7}
    # Half of the maximum speeds in Kujala et al. (2018), seemed like a reasonable starting point
    speed_dict = {"tram": 50, "subway": 75, "rail": 150, "bus": 50, "ferry": 40, "cablecar": 25, "gondola": 25, "funicular": 25}
    mode_list = []
    file_names = os.listdir(cwd)  
    for file_name in file_names:
        if file_name in [f"network_{mode}.csv" for mode in mode_dict.keys()]:
            mode = file_name.split(".")[0][8:]
            mode_list.append(mode)
    mode_list.append("walk")
    with open("./basis/Config.cnf", "a") as file:
        file.write(f"modalities_all; [{",".join(mode_list)}]\n")
        for mode in mode_list:
            if mode != "walk":
                file.write(f"{mode}.modality_category; line-based\n")
                file.write(f"{mode}.gen_vehicle_speed; {speed_dict[mode]}\n")
            else:
                file.write(f"{mode}.modality_category; nonscheduled\n")
                file.write(f"{mode}.gen_vehicle_speed; 5\n")
        file.write(config_strs[1]) # Write the second half of the config string
    logging.info("Finished creating Config files")

    logging.info("Begin creating Makefile")
    makefile_str = get_makefile_str()
    makefile_name = "Makefile"
    with open("./{}".format(makefile_name), "w") as text_file:
        text_file.write(makefile_str)
    for foldername in ["line-planning", "delay-management", "graphics", "tariff", "timetabling", "vehicle-scheduling"]:
        makefile_str = get_folder_makefile_str(foldername)
        makefile_name = foldername+"/Makefile"
        with open("./{}".format(makefile_name), "w") as text_file:
            text_file.write(makefile_str)
    logging.info("Finished creating Makefile")

def read_non_isolated_nodes(network_nodes_file: str, network_file: str) -> pd.DataFrame:
    """
    This function reads the node and edge files, and returns a dataframe representation of the nodes 
    where the "isolated nodes" are removed. A node is considered isolated if no edge connects to it

    :param network_nodes_file: name of csv-file that has information on the stops
    :type network_nodes_file: str
    :param network_file: name of csv-file that has information on the edges
    :type network_file: str
    :return: a dataframe containing the non-isolated nodes
    """
    edges_df = pd.read_table(network_file, delimiter=";", header=None, skiprows=1,
                             names=["from_stop", "to_stop", "length"], usecols=[0, 1, 2], comment='#')
    stops_df = pd.read_table(network_nodes_file, delimiter=";", header=None, skiprows=1,
                             names=["stop-id", "latitude", "longitude", "long-name"], comment='#')
    used_nodes = set(edges_df["from_stop"].values)|set(edges_df["to_stop"].values)
    removed_nodes = set(stops_df["stop-id"].values) - used_nodes
    logging.debug("Removed {} isolated nodes out of the total of {} nodes".format(len(removed_nodes), len(stops_df["stop-id"].values)))
    stops_df = stops_df[stops_df["stop-id"].isin(used_nodes)]
    return stops_df

def create_stop_files(transportation_mode: int, city_name: str, removed_nodes_map: dict[int, int], config: Config) -> None:
    """
    Reads the stop information from network_nodes.csv, and writes it to the LinTim file basis/<modality>.Stop.giv

    :param transportation_mode: an integer corresponding to the modality (following the GTFS standard), or -1 for walking
    :type transportation_mode: int
    :param city_name: name of the city, used in coordinate conversions
    :type city_name: str
    :param removed_nodes_map: a dictionary where the keys are stop IDs before node merging and values are IDs after merging
    :type removed_nodes_map: dict[int, int]
    :param config: The configuration used
    :type config: Config
    """
    network_file = "network_{}.csv".format(get_transp_mode_str(transportation_mode=transportation_mode))
    network_nodes_file = "./network_nodes.csv"
    stops_df = read_non_isolated_nodes(network_nodes_file=network_nodes_file, network_file=network_file)
    
    # Apply stop merging
    stops_df["stop-id"] = stops_df["stop-id"].apply(lambda x: removed_nodes_map[x])
    stops_df.drop_duplicates(subset="stop-id", inplace=True, ignore_index=True)

    geo_stops_file = config.getStringValue("default_stops_coordinates_file")
    geo_stop_header = "#"+config.getStringValue("stops_coordinates_header")
    stops_df[["stop-id", "latitude", "longitude"]].to_csv(path_or_buf=geo_stops_file, sep=";",
                                                          header=geo_stop_header.split("; "), index=False)
    
    input_epsg, output_epsg = get_input_and_output_epsg(city_name=city_name)
    def fct_lat_lon_to_coordinates(lat, lon):
        transformer = pyproj.Transformer.from_crs(input_epsg, output_epsg)
        return transformer.transform(lat,lon)
    coordinates = stops_df[["latitude", "longitude"]].apply(
        lambda x: fct_lat_lon_to_coordinates(x["latitude"], x["longitude"]), axis=1)
    coordinates = list(zip(*coordinates))
    stops_df["x-coordinate"] = coordinates[0]
    stops_df["y-coordinate"] = coordinates[1]
    stop_file = config.getStringValue("default_stops_file")
    stop_header = "#"+config.getStringValue("stops_header")
    stops_df[["stop-id", "stop-id", "long-name", "x-coordinate", "y-coordinate"]].to_csv(path_or_buf=stop_file, sep=";",
                                                                                  header=stop_header.split("; "), index=False)

def create_edge_files(transportation_mode: int, removed_nodes_map: dict[int, int], config: Config) -> None:
    """
    Reads the edge information from the network csv file, and writes it to the LinTim file basis/<modality>.Edge.giv

    :param transportation_mode: an integer corresponding to the modality (following the GTFS standard), or -1 for walking
    :type transportation_mode: int
    :param removed_nodes_map: a dictionary where the keys are stop IDs before node merging and values are IDs after merging
    :type removed_nodes_map: dict[int, int]
    :param config: The configuration used
    :type config: Config
    """
    transp_mode_str = get_transp_mode_str(transportation_mode)
    network_file = "./network_{}.csv".format(transp_mode_str)
    if not os.path.isfile(network_file):
        logging.error("Network file {} does not exist".format(network_file))
        exit(1)
        
    if transportation_mode == -1:
        # Get walking idstance instead of beeline distance
        edges_df = pd.read_table(network_file, delimiter=";", header=None, skiprows=1,
                             names=["from_stop", "to_stop", "length"], usecols=[0, 1, 3], comment='#')
    else:
        edges_df = pd.read_table(network_file, delimiter=";", header=None, skiprows=1,
                             names=["from_stop", "to_stop", "length", "duration_avg"], usecols=[0, 1, 2, 3], comment='#')

    # link_index; from_stop; to_stop; length; lower_bound; upper_bound
    edges_df["length"] = edges_df["length"] / 1000 # from meters to kilometers
    if transportation_mode == -1:
        edges_df["lower_bound"] = np.maximum(1, np.floor((edges_df["length"] / 4)*60)) # assume 4 km/h walking speed
        edges_df["upper_bound"] = edges_df["lower_bound"]+config.getIntegerValue("period_length")
    else:
        # lower bound is somewhat arbitrarily 80% of average duration on edge in minutes, rounded down, but at least 1
        edges_df["lower_bound"] = np.maximum(1, np.floor(edges_df["duration_avg"]*0.8 / 60))
        edges_df["upper_bound"] = edges_df["lower_bound"]*3
        edges_df.drop(columns=["duration_avg"], inplace=True)
    edges_df["link_index"] = range(1, len(edges_df["length"])+1)
    # use the dictionarys to edit the dataframe and switch to new stop ids
    left_stop_id_pc = [removed_nodes_map[left_stop_id] if left_stop_id in removed_nodes_map else left_stop_id for left_stop_id in edges_df["from_stop"].values]
    right_stop_id_pc = [removed_nodes_map[right_stop_id] if right_stop_id in removed_nodes_map else right_stop_id for right_stop_id in edges_df["to_stop"].values]
    edges_df["left-stop-id-post-comb"] = left_stop_id_pc
    edges_df["right-stop-id-post-comb"] = right_stop_id_pc

    total_edges = len(edges_df)

    # remove loops
    edges_df[edges_df["left-stop-id-post-comb"] != edges_df["right-stop-id-post-comb"]].reset_index(drop=True, inplace=True)
    # merge edges between same nodes
    ids = edges_df["link_index"].unique()
    removed_edges_map = dict(zip(ids, ids))
    edge_agg = edges_df.groupby(['left-stop-id-post-comb','right-stop-id-post-comb'], as_index=False).agg(
        count=('link_index', 'count'), avg_length=('length', 'mean'), avg_lb=('lower_bound', 'mean'), avg_ub=('upper_bound', 'mean'))
    max_index = max(edges_df['link_index'].values)
    link_index = Counter(first_value=max_index)
    rows_to_delete = []
    combined_edges = pd.DataFrame(columns=list(edges_df.columns))
    for index, row in edge_agg.iterrows():
        if row['count'] > 1:
            new_index = link_index()
            for i in edges_df.index[(edges_df['left-stop-id-post-comb'] == row['left-stop-id-post-comb']) & (edges_df['right-stop-id-post-comb'] == row['right-stop-id-post-comb'])].to_list():
                rows_to_delete.append(i)
                removed_edges_map[edges_df.iloc[i,:]["link_index"]] = new_index
            new_edge = pd.DataFrame([[row['left-stop-id-post-comb'], row['right-stop-id-post-comb'], float(row['avg_length']), int(row['avg_lb']), int(row['avg_ub']), 
                                      new_index, row['left-stop-id-post-comb'], row['right-stop-id-post-comb']]], columns=list(edges_df.columns))
            
            if not combined_edges.empty:
                combined_edges = pd.concat([combined_edges, new_edge], ignore_index=True)
            else:
                combined_edges = new_edge
    edges_df.drop(edges_df.index[rows_to_delete], inplace=True)
    if not combined_edges.empty:
        edges_df = pd.concat([edges_df, combined_edges], ignore_index=True)
    edges_df.reset_index(drop=True, inplace=True)
    logging.debug(f"Went from {total_edges} edges to {len(edges_df)} edges.")

    # save the edge file
    edge_file = config.getStringValue("default_edges_file")
    edge_header = "#"+config.getStringValue("edges_header")
    edges_df[["link_index", "left-stop-id-post-comb", "right-stop-id-post-comb", "length", "lower_bound", "upper_bound"]].astype(
        {"link_index": int, "left-stop-id-post-comb": int, "right-stop-id-post-comb": int, "length" : float, "lower_bound" : int, "upper_bound" : int}).to_csv(
        path_or_buf=edge_file, sep=";",
        header=edge_header.split("; "), index=False, float_format="%.5g")


def create_ptn_files(transportation_mode, city_name, removed_nodes_map, config) -> None:
    """
    Reads the stop and edge information and writes it to LinTim format

    :param transportation_mode: an integer corresponding to the modality (following the GTFS standard), or -1 for walking
    :type transportation_mode: int
    :param city_name: name of the city, used in coordinate conversions
    :type city_name: str
    :param removed_nodes_map: a dictionary where the keys are stop IDs before node merging and values are IDs after merging
    :type removed_nodes_map: dict[int, int]
    :param config: The configuration used
    :type config: Config
    """
    create_stop_files(transportation_mode=transportation_mode, city_name=city_name, removed_nodes_map=removed_nodes_map, config=config)
    create_edge_files(transportation_mode=transportation_mode, removed_nodes_map=removed_nodes_map, config=config)


def get_timestamp_hour(x) -> int:
    return datetime.fromtimestamp(x).hour

def get_timestamp_minute(x) -> int:
    return datetime.fromtimestamp(x).minute


def read_line_information(df_ntd: pd.DataFrame, df_edges: pd.DataFrame) -> tuple[dict[int,int], dict[int,int], pd.DataFrame]:
    """
    This function reads the linepool information from the dataset. Most line numbers in the data
    correspond to more than one series of stops, and this function creates a separate line for each combination.

    :param df_ntd: dataframe that represents the file network_temporal_day.csv
    :type df_ntd: pd.DataFrame
    :param df_edges: dataframe that represents the file "Edge.giv"
    :type df_edges: pd.DataFrame
    :return: a dictionary mapping the line numbers in df_ntd to new line numbers, a dictionary mapping the new line numbers to trip numbers in df_ntd, and a processed dataframe containing the line information
    :rtype: tuple[dict[int,int], dict[int,int], pd.DataFrame]
    """
    # create and empty dataframe that will be filled with the the information of the line-concept
    # It is required to give it column names in advance, otherwise filling the dataframe via concatenation
    # will lead to errors
    column_names = list(df_ntd.columns)
    column_names.append("frequency")
    line_complete_df = pd.DataFrame(columns=column_names)

    # A dataframe containing only the first segment of each trip
    df_seq = df_ntd[df_ntd["seq"] == 1]

    # Map trips and lines
    line_trip_tuples = list(zip(df_seq["route_I"].values, df_seq["trip_I"].values))
    line_trip_dct = {}
    for line_id, trip_id in line_trip_tuples:
        if line_id in line_trip_dct.keys():
            line_trip_dct[line_id].append(trip_id)
        else:
            line_trip_dct[line_id] = [trip_id]

    zeros_to_add = len(str(max(df_ntd["route_I"]))) - 1
    old_new_line_dict = {line_id: [] for line_id in line_trip_dct.keys()}
    new_lines_dict = {}
    new_lines_freq = {}

    trips_in_new_lines_dct = {}
    for line_id in line_trip_dct.keys():
        counter = 1
        for trip_id in line_trip_dct[line_id]:
            # Get the rows corresponding to given trip
            df_trip_in_route = df_ntd.loc[df_ntd["trip_I"]==trip_id].copy()
            l_start_stop = len(df_trip_in_route.index)
            if l_start_stop == 0:
                logging.error("No information on line", line_id, "trip", trip_id)
                exit(1)
            # Get the list of stops and check if the same list of stops (i.e., the same subline) has already been encountered
            stops_in_trip = list(df_trip_in_route["from_stop_I"].values)+[df_trip_in_route["to_stop_I"].values[l_start_stop-1]]
            if stops_in_trip in list(new_lines_dict.values()):
                val_list = list(new_lines_dict.values())
                key_list = list(new_lines_dict.keys())
                position = val_list.index(stops_in_trip)
                new_line_id = key_list[position]
                new_lines_freq[new_line_id] += 1
                trips_in_new_lines_dct[new_line_id].append(int(trip_id))
            else:
                new_line_id = int(str(line_id) + "0" * zeros_to_add + str(counter))
                counter += 1

                new_lines_dict[new_line_id] = stops_in_trip
                new_lines_freq[new_line_id] = 1
                trips_in_new_lines_dct[new_line_id] = [int(trip_id)]

                old_new_line_dict[line_id].append(new_line_id)

                df_trip_in_route["route_I"] = [new_line_id] * l_start_stop
                df_trip_in_route["edge-order"] = df_trip_in_route["seq"]  
                # Concatenate the dataframe of this line with the one of the other lines.
                # Note that frequency will be set later
                line_complete_df = pd.concat([line_complete_df, df_trip_in_route], ignore_index=True)
    for new_line_id in new_lines_dict.keys():
        frequency = new_lines_freq[new_line_id]
        df_new_line_id = line_complete_df[line_complete_df["route_I"] == new_line_id]
        index_list_new_line = df_new_line_id.index
        line_complete_df.loc[index_list_new_line,"frequency"] = [frequency]*len(index_list_new_line)
    if line_complete_df.isnull().any()["frequency"]:
        not_set_with_multipels = line_complete_df[line_complete_df["frequency"].isnull()]["route_I"].values
        not_set_df = list(set(not_set_with_multipels))
        logging.fatal("Frequency of (sub-)lines {} are not set or only partially set.".format(not_set_df))

    logging.debug("Identify edge id's from columns with the left and right stop id")
    edge_id_list = []
    progress_counter = 0.2
    for i in range(len(line_complete_df.index)):
        if i == np.ceil(progress_counter * len(line_complete_df.index)):
            logging.debug("{} percent of edges iterated. {} remaining edge id's to identify"
                          .format(round(progress_counter, 1)*100, int((1-progress_counter)*len(df_ntd.index))))
            progress_counter += 0.2
        left_stop_id = int(line_complete_df.iloc[i]["from_stop_I"])
        right_stop_id = int(line_complete_df.iloc[i]["to_stop_I"])
        edge_row = df_edges.loc[(df_edges["left-stop-id"] == left_stop_id) & (df_edges["right-stop-id"] == right_stop_id)]
        if edge_row.empty:
            logging.error(f"edge between stops {left_stop_id} and {right_stop_id} not found.")
        elif len(edge_row) > 1:
            logging.warning(f"more than one edge between stops {left_stop_id} and {right_stop_id} found, using the first.")
            edge_row = edge_row.iloc[0]
            edge_id = int(edge_row["edge-id"])
            edge_id_list.append(edge_id)
        else:
            edge_id = int(edge_row["edge-id"].iloc[0])
            edge_id_list.append(edge_id)

    # line-id = route_I, edge-order = seq, edge-id = edge-id, frequency =
    line_complete_df["edge-id"] = edge_id_list
    with open('./Debug/old_new_line_dict.csv', 'w') as csv_file:
        writer = csv.writer(csv_file, delimiter=';')
        writer.writerow(["old line id", "subline_ids"])
        for key, value in old_new_line_dict.items():
            writer.writerow([key, value])
    with open('./Debug/old_trips_in_network_temp_day_for_sublines.csv', 'w') as csv_file:
        writer = csv.writer(csv_file, delimiter=';')
        writer.writerow(["(sub-)line id", "trip_id_in_network_temporal_day"])
        for key, value in trips_in_new_lines_dct.items():
            writer.writerow([key, value])
    return old_new_line_dict, trips_in_new_lines_dct, line_complete_df


def write_lc(line_complete_df: pd.DataFrame, df_ntd: pd.DataFrame, config: Config, old_new_line_dict: dict[int,int], new_line_trip_dct: dict[int,int], start_time: str):
    """
    This function uses the line information collected by the read_line_information -function
    and writes the line concept for a representative hour to line-planning/Line-Concept.lin

    :param line_complete_df: a processed dataframe containing the line information
    :type line_complete_df: pd.DataFrame
    :param df_ntd: dataframe that represents the file network_temporal_day.csv
    :type df_ntd: pd.DataFrame
    :param old_new_line_dict: a dictionary mapping the line numbers in df_ntd to new line numbers
    :type old_new_line_dict: dict[int,int]
    :param new_line_trip_dct: a dictionary mapping the new line numbers to trip numbers in df_ntd
    :type new_line_trip_dct: dict[int,int]
    :param start_time: the start time of the representative hour in "hh:mm" format
    :type start_time: str
    """
    hour, minutes = start_time.split(':')
    hour = int(hour)
    minutes = int(minutes)
    all_trips_hour = list(set(df_ntd[(df_ntd["seq"] == 1) & (((df_ntd["dep_hour"] == hour) & (df_ntd["dep_minute"] >= minutes)) | ((df_ntd["dep_hour"] == (hour+1)%24) & (df_ntd["dep_minute"] < minutes)))]["trip_I"].values))
    df_hour = df_ntd.loc[(df_ntd["trip_I"].isin(all_trips_hour)) & (df_ntd["seq"]==1)].copy()
    dropped_sublines = []
    for subline_id, trip_list in new_line_trip_dct.items():
        freq = len(df_hour.loc[df_hour["trip_I"].isin(trip_list)])
        if freq > 0:
            line_complete_df.loc[line_complete_df["route_I"] == subline_id, "frequency"] = freq
        else:
            index_drop = line_complete_df.loc[line_complete_df["route_I"] == subline_id].index
            line_complete_df.drop(index_drop, inplace=True)
            dropped_sublines.append(subline_id)
    # save the data in a line-concept file. Typically it has the header "line-id; edge-order; edge-id; frequency"
    line_df = line_complete_df[["route_I", "edge-order", "edge-id", "frequency"]]
    line_df = line_df.astype('int32')
    line_concept_file = config.getStringValue("default_lines_file")
    line_header = "#"+config.getStringValue("lines_header")
    line_df.to_csv(path_or_buf=line_concept_file, sep=";", header=line_header.split("; "),
                   index=False)
    # count lines
    more_then_three_sublines_count = 0
    for line_id, new_line_id_list in old_new_line_dict.items():
        new_line_id_list_hour = [subline_id for subline_id in new_line_id_list if subline_id not in dropped_sublines]
        if len(new_line_id_list_hour) > 2:
            logging.debug(f"line {line_id} sublist id {new_line_id_list_hour}")
            more_then_three_sublines_count += 1
    logging.debug("For the representative hour, more than three sublines in {} of {} lines"
                  .format(more_then_three_sublines_count, len(old_new_line_dict.keys())))

def get_transp_mode_str(transportation_mode: int) -> str:
    """
    returns the name of the modality corresponding to the GTFS standard modality number

    :param transportation_mode: an integer corresponding to the modality (following the GTFS standard), or -1 for walking
    :type transportation_mode: int
    :return: name of the modality
    :rtype: str
    """
    if not isinstance(transportation_mode,int):
        logging.error("transportation mode should be of type int")
        exit(1)
    if transportation_mode < -1 or transportation_mode > 7:
        logging.error("transportation mode should be between 0 and 7, but is {}".format(transportation_mode))
        exit(1)
    if transportation_mode == -1:
        return "walk"    
    if transportation_mode == 0:
        return "tram"
    if transportation_mode == 1:
        return "subway"
    if transportation_mode == 2:
        return "rail"
    if transportation_mode == 3:
        return "bus"
    if transportation_mode == 4:
        return "ferry"
    if transportation_mode == 5:
        return "cablecar"
    if transportation_mode == 6:
        return "gondola"
    if transportation_mode == 7:
        return "furnicular"
    return

def get_input_and_output_epsg(city_name: str) -> tuple[str,str]:
    """
    returns the epsg code for one of the of the 25 cities in the paper
    This function is later used to convert the Geo-coordinates into usable x-and y-coordinates

    :param city_name: name of one of the 25 cities in the paper
    :type city_name: str
    :return: input and output epsg codes for the city
    """
    if not isinstance(city_name, str):
        logging.error("To get an epsg format, input a name of a city, not {}".format(city_name))
        exit(1)

    city_name_list = ["adelaide", "belfast", "berlin", "bordeaux", "brisbane", "canberra", "detroit", "dublin", "grenoble",
                 "helsinki", "kuopio", "lisbon", "luxenbourg", "melbourne", "sydney", "nantes", "palermo", "paris",
                 "prague", "rome", "rennes", "toulouse", "turku", "venice", "winnipeg"]

    if city_name in city_name_list:
        input_epsg = "epsg:4326"
        output_epsg = "epsg:3857"
    else:
        logging.error("EPSG code for {} could not be set".format(city_name))
        exit(1)
    return input_epsg, output_epsg


def convert_timetable(df_ntd: pd.DataFrame, config: Config, lines: list[Line], new_line_trip_dct: dict[int, int], start_time: str) -> None: 
    """
    Extracts the timetable from data into LinTim format (periodic EAN + timetable)
    :param df_ntd: dataframe that represents the file network_temporal_day.csv
    :type df_ntd: pd.DataFrame
    :param config: configuration read from Config.cnf and other files
    :type config: Config
    :param lines: linepool structure
    :type lines: list[Line]
    :param new_line_trip_dct: a dictionary mapping the new line numbers to trip numbers in df_ntd
    :type new_line_trip_dct: dict[int, int]
    :param start_time: the start time of the representative hour in "hh:mm" format
    :type start_time: str
    """
    hour, minutes = start_time.split(':')
    hour = int(hour)
    minutes = int(minutes)
    all_trips_hour = list(set(df_ntd[(df_ntd["seq"] == 1) & (((df_ntd["dep_hour"] == hour) & (df_ntd["dep_minute"] >= minutes)) | ((df_ntd["dep_hour"] == (hour+1)%24) & (df_ntd["dep_minute"] < minutes)))]["trip_I"].values))
    df_hour = df_ntd.loc[df_ntd["trip_I"].isin(all_trips_hour)].copy()

    ean = SimpleDictGraph()
    event_id = Counter()
    activity_id = Counter()
    minimal_wait_time = config.getDoubleValue("ean_default_minimal_waiting_time")
    maximal_wait_time = config.getDoubleValue("ean_default_maximal_waiting_time")
    minimal_change_time = config.getDoubleValue("ean_default_minimal_change_time")
    maximal_change_time = config.getDoubleValue("ean_default_maximal_change_time")
    arrivals = []
    departures = []
    for subline in lines.getLines():
        subline_path = subline.getLinePath()
        subline_id = subline.getId()
        subline_freq = subline.getFrequency()

        trips = new_line_trip_dct[subline_id]
        trips_hour = list(set(df_hour[df_hour["trip_I"].isin(trips)]["trip_I"].values))

        if len(trips_hour) != subline_freq:
            logging.fatal("Frequency of {} is {} but the data only provides event times for a line of frequency {}"
                          .format(subline_id, subline_freq, len(trips_hour)))

        trip_departures = [df_hour.loc[(df_hour["seq"]==1) & (df_hour["trip_I"]==trip_id)]["dep_minute"].values[0] for trip_id in trips_hour]
        trips_sorted = [x for _, x in sorted(zip(trip_departures, trips_hour))]
        oldDepartureEvents = {}
        for i, trip_id in enumerate(trips_sorted):
            df_trip = df_hour[df_hour["trip_I"] == trip_id]
            line_rep = i+1  
            for j, edge in enumerate(subline_path.getEdges()):
                from_stop_id = edge.getLeftNode().getId()
                if from_stop_id != df_trip[df_trip["seq"] == j+1]["from_stop_I"].values[0]:
                    logging.error(f"Data inconsistent: segment number {j+1} on line {subline_id} starts from stop {from_stop_id} or {df_trip[df_trip["seq"] == j+1]["from_stop_I"].values[0]}")
                dep_time = df_trip[df_trip["seq"] == j+1]["dep_minute"].values[0]
                dep_event = PeriodicEvent(event_id(), from_stop_id, EventType.DEPARTURE,
                                                    subline_id, dep_time, 0,
                                                    LineDirection.FORWARDS, line_rep)
                ean.addNode(dep_event)
                departures.append(dep_event)

                if line_rep > 1:
                    time_diff = dep_event.getTime()-oldDepartureEvents[j].getTime()
                    if time_diff < 0:
                        time_diff += 60
                    sync = PeriodicActivity(activity_id(), ActivityType.SYNC,
                                            oldDepartureEvents[j], dep_event,
                                            time_diff, time_diff, 0)
                    ean.addEdge(sync)
                oldDepartureEvents[j] = dep_event

                if j >= 1:
                    wait = PeriodicActivity(activity_id(), ActivityType.WAIT,
                                            arr_event, dep_event,
                                            minimal_wait_time, maximal_wait_time, 0)
                    ean.addEdge(wait)

                to_stop_id = edge.getRightNode().getId()
                arr_time = df_trip[df_trip["seq"] == j+1]["arr_minute"].values[0]
                arr_event = PeriodicEvent(event_id(), to_stop_id, EventType.ARRIVAL,
                                                    subline_id, arr_time, 0,
                                                    LineDirection.FORWARDS, line_rep)
                ean.addNode(arr_event)
                arrivals.append(arr_event)

                drive = PeriodicActivity(activity_id(), ActivityType.DRIVE,
                                         dep_event, arr_event,
                                         edge.getLowerBound(), edge.getUpperBound(), 0)
                ean.addEdge(drive)

    for arrival in arrivals:
        for departure in departures:
            if (arrival.getStopId() == departure.getStopId()) and (arrival.getLineId() != departure.getLineId()):
                change = PeriodicActivity(activity_id(), ActivityType.CHANGE,
                                         arrival, departure,
                                         minimal_change_time, maximal_change_time, 0)
                ean.addEdge(change)
    PeriodicEANWriter.write(ean=ean, write_timetable=True)


def print_timetable(ptn: Graph[Stop, Link], df_ntd: pd.DataFrame, new_line_trip_dct: dict[int, int], subline: Line):
    """
    A helper function for printing the timetable of a subline
    :param ptn: the PTN of the line
    :type ptn: Graph[Stop, Link]
    :param df_ntd: dataframe that represents the file network_temporal_day.csv
    :type df_ntd: pd.DataFrame
    :param new_line_trip_dct: a dictionary mapping the new line numbers to trip numbers in df_ntd
    :type new_line_trip_dct: dict[int, int]
    :param subline: the line whose timetable should be printed
    :type subline: Line
    """
    subline_path = subline.getLinePath()
    subline_id = subline.getId()
    departures_per_hour = {h:[] for h in range(24)}
    for trip_id in new_line_trip_dct[subline_id]:
        df_trip = df_ntd[df_ntd["trip_I"] == trip_id].copy()
        hour = df_trip.loc[df_trip["seq"]==1]["dep_hour"].values[0]
        minute = df_trip.loc[df_trip["seq"]==1]["dep_minute"].values[0]
        departures_per_hour[hour].append(minute)
    start_stop_id = df_ntd.loc[((df_ntd["trip_I"] == new_line_trip_dct[subline_id][0]) & (df_ntd["seq"] == 1))]["from_stop_I"].values[0]
    to_stop_id = df_ntd.loc[((df_ntd["trip_I"] == new_line_trip_dct[subline_id][0]) & (df_ntd["seq"] == len(subline_path.getEdges())))]["to_stop_I"].values[0]
    print(f"Line {subline_id} from {ptn.getNode(start_stop_id).getLongName()} to {ptn.getNode(to_stop_id).getLongName()}")
    for h in range(24):
        print(f"{h}| {" ".join(str(i) for i in sorted(departures_per_hour[h]))}")


def combine_opposite_stops(city_name: str) -> dict[int,int]:
    """
    Reduced the stops of the directed PTN by combines opposite stops. Opposite stops are classified
    by their name and location
    :param city_name: the name of the city in question
    :type city_name: str
    :return: a dictionary where the keys are stop IDs before node merging and values are IDs after merging
    :rtype: dict[int,int]
    """
    # read stop file
    network_nodes_file = "./network_nodes.csv"
    stops_df = pd.read_table(network_nodes_file, delimiter=";", header=None, skiprows=1,
                             names=["stop-id", "latitude", "longitude", "long-name"], comment='#')
    # shorthand for convenience
    df = stops_df

    # Transform latitude and longitude to x- and y-coordinates
    input_epsg, output_epsg = get_input_and_output_epsg(city_name=city_name)
    def fct_lat_lon_to_coordinates(lat, lon):
        transformer = pyproj.Transformer.from_crs(input_epsg, output_epsg)
        return transformer.transform(lon, lat)
    coordinates = stops_df[["latitude", "longitude"]].apply(
        lambda x: fct_lat_lon_to_coordinates(x["latitude"], x["longitude"]), axis=1)
    coordinates = list(zip(*coordinates))
    stops_df["x-coordinate"] = coordinates[0]
    stops_df["y-coordinate"] = coordinates[1]
    stops_df.drop(columns=["latitude", "longitude"], inplace=True)

    df[['x_avg', 'y_avg']] = df[['long-name','x-coordinate','y-coordinate']].groupby(['long-name']).transform('mean')
    df['count_name'] = df[['long-name','stop-id']].groupby(['long-name']).transform('count')
    # Cartesian distance might be "wrong" when cities are far from the equator, but should be roughly "consistently wrong" within a city 
    # Haversine would be correct but was way too slow to compute
    df['dist'] = df.apply(lambda row: math.sqrt((row['x-coordinate']-row['x_avg'])**2 + (row['y-coordinate']-row['y_avg'])**2), axis=1)

    # count how many duplicates, triplicates
    counts_df = df["long-name"].value_counts()
    total_stops = len(df)
    stay_as_is = len(counts_df[counts_df == 1])
    more_than_two = len(counts_df[counts_df > 2])
    logging.debug("{} out of {} stops have no duplicate names".format(stay_as_is, total_stops))
    logging.debug("There are {} instances of more than two stops with duplicate names".format(more_than_two))

    threshold = 500 # meters, roughly
    running_index = df["stop-id"].max()+1
    rows_to_delete = []
    ids = df["stop-id"].unique()
    removed_nodes_map = dict(zip(ids, ids))
    for idx in df.index:
        if not (idx in rows_to_delete):
            if df.at[idx,"count_name"] > 1:
                df_temp = df.loc[df['long-name'] == df.at[idx,"long-name"]]
                for i in df.index[df['long-name'] == df.at[idx,"long-name"]].to_list():
                    rows_to_delete.append(i)
                if df_temp["dist"].max() >= threshold:
                    distance_matrix = np.array([[math.sqrt((df_temp.loc[i,'x-coordinate']-df_temp.loc[j,'x-coordinate'])**2 + (df_temp.loc[i,'y-coordinate']-df_temp.loc[j,'y-coordinate'])**2) for i in df_temp.index] for j in df_temp.index])
                    clustering = AgglomerativeClustering(metric="precomputed", distance_threshold=2*threshold, n_clusters=None, linkage="average").fit(distance_matrix)
                    for i in range(clustering.n_clusters_):
                        cluster_index = df_temp.index[np.where(clustering.labels_ == i)[0]]
                        x_avg = df_temp.loc[cluster_index, "x-coordinate"].mean()
                        y_avg = df_temp.loc[cluster_index, "y-coordinate"].mean()
                        "stop-id", "short-name", "long-name", "x-coordinate", "y-coordinate"
                        df.loc[len(df)] = [running_index, f"{df.at[idx,'long-name']}_{i}", x_avg, y_avg, x_avg, y_avg, 1, 0]
                        for j in cluster_index:
                            removed_nodes_map[df_temp.loc[j, "stop-id"]] = running_index
                        running_index += 1
                else:
                    df.loc[len(df)] = [running_index, df.at[idx,"long-name"], df.at[idx,"x_avg"], df.at[idx,"y_avg"], df.at[idx,"x_avg"], df.at[idx,"y_avg"], 1, 0]
                    for index, row in df_temp.iterrows():
                        removed_nodes_map[row["stop-id"]] = running_index
                    running_index += 1
    df.drop(df.index[rows_to_delete], inplace=True)
    df.reset_index(drop=True, inplace=True)
    logging.debug(f"Went from {total_stops} nodes to {len(df)} nodes.")

    stop_ids = sorted(df["stop-id"].unique())
    consecutive_map = {k: v for k, v in zip(stop_ids, range(1,len(stop_ids)+1))}
    df["stop-id"] = df["stop-id"].apply(lambda x: consecutive_map[x])
    removed_nodes_map_consecutive = {k: v for k, v in zip(removed_nodes_map.keys(), [consecutive_map[removed_nodes_map[old_id]] for old_id in removed_nodes_map])}
    # save the reference stop file
    reference_df = pd.DataFrame({"LinTim node id": removed_nodes_map_consecutive.values(), "original node id": removed_nodes_map_consecutive.keys()})
    reference_file = "./Debug/basis/reference_node_id.txt"
    reference_df.to_csv(path_or_buf=reference_file, sep=";", index=False)
    
    return removed_nodes_map_consecutive

def write_connected_components(ptn: Graph[Stop, Link], mode: int) -> None:
    """
    A helper function for writing the connected components of a PTN
    :param ptn: the PTN of the line
    :type ptn: Graph[Stop, Link]
    :param mode: an integer corresponding to the modality (following the GTFS standard), or -1 for walking
    :type mode: int
    """
    # make the ptn into a network x graph
    # 1. create empty nx graph
    nx_ptn = nx.Graph()
    # 2. add nodes
    for node in ptn.getNodes():
        nx_ptn.add_node(node)
    # 3. add edges
    for edge in ptn.getEdges():
        u,v = edge.getLeftNode(), edge.getRightNode()
        edge_id = edge.getId()
        nx_ptn.add_edge(u,v, edge_id=edge_id)

    # compute the connected components of the ptn
    conn_comps_in_ptn = [nx_ptn.subgraph(c) for c in nx.connected_components(nx_ptn)]

    # Iterate the components, make a graph object that represents the connected component of the PTN and write it into the basis folder
    for i, c in enumerate(conn_comps_in_ptn):
        # make the ptn of the connected component as a graph object
        ptn_comp = SimpleDictGraph() # Graph()
        for node in c.nodes():
            ptn_comp.addNode(node)
        for (u, v) in c.edges():
            edge_id = c[u][v]["edge_id"]
            edge = ptn.getEdge(edge_id)
            ptn_comp.addEdge(edge)
        # write the ptn in an individual file
        if not os.path.isdir("./Debug/basis"):
            os.mkdir("./Debug/basis")
        node_file = "./Debug/basis/{}.Stop_subgraph_{}.giv".format(get_transp_mode_str(mode),i)
        edge_file = "./Debug/basis/{}.Edge_subgraph_{}.giv".format(get_transp_mode_str(mode),i)
        PTNWriter.write(ptn=ptn_comp, stop_file_name=node_file, link_file_name=edge_file)
    return conn_comps_in_ptn


def separate_line_concept_into_components(linepool: LinePool, components: list[nx.Graph], mode) -> None:
    """
    Reads a linepool and a list of components and writes the lines for the components

    :param linepool: linepool
    :type linepool: LinePool
    :param components: list of graphs with the components
    :type components: list[nx.Graph]
    :param mode: an integer corresponding to the modality (following the GTFS standard), or -1 for walking
    :type mode: int
    """
    for i, component in enumerate(components):
        comp_linepool = LinePool()
        for line in linepool.getLines():
            line_path = line.getLinePath()
            # get the stops in the line
            stops_in_line = line_path.getNodes()
            # check if the stops in the line belong to that component
            # and use counter to check if indeed all stops in the line belong to that component
            # i.e. the line doesn't have to be split up
            belongs_to_num_comp = 0
            for stop in stops_in_line:
                if stop in component.nodes():
                    comp_linepool.addLine(line)
                    belongs_to_num_comp += 1
            # give error message if line goes through multiple components
            if belongs_to_num_comp != 0 and belongs_to_num_comp != len(stops_in_line):
                logging.warning("line goes through unconnected components")
                exit(1)
        if not os.path.isdir("./Debug/line-planning"):
            os.mkdir("./Debug/line-planning")
        # write line concept
        line_concept_file = "Debug/line-planning/{}.Line-Concept-subgraph-{}.lin".format(get_transp_mode_str(mode),i)
        cost_file = "Debug/line-planning/{}.Pool-Cost-subgraph-{}.giv".format(get_transp_mode_str(mode),i)
        LineWriter.write(pool=comp_linepool, write_pool=False, write_line_concept=True,
                         concept_file_name=line_concept_file)
    return

def process_trip_data(transportation_mode: int, removed_nodes_map: dict[int, int]) -> pd.DataFrame:
    """
    Processes the data in network_temporal_day.csv
    
    :param transportation_mode: an integer corresponding to the modality (following the GTFS standard), or -1 for walking
    :type transportation_mode: int
    :param removed_nodes_map: a dictionary where the keys are stop IDs before node merging and values are IDs after merging
    :type removed_nodes_map: dict[int, int]
    :return: dataframe that represents the file network_temporal_day.csv
    :rtype: pd.DataFrame
    """
    df_ntd = pd.read_table("./network_temporal_day.csv", delimiter=";")
    df_ntd = df_ntd.loc[df_ntd["route_type"] == transportation_mode]
    df_ntd["dep_hour"] = df_ntd["dep_time_ut"].apply(get_timestamp_hour).values
    df_ntd["dep_minute"] = df_ntd["dep_time_ut"].apply(get_timestamp_minute).values
    df_ntd["arr_hour"] = df_ntd["arr_time_ut"].apply(get_timestamp_hour).values
    df_ntd["arr_minute"] = df_ntd["arr_time_ut"].apply(get_timestamp_minute).values

    # Remove self edges and adjust edge ordering to account for this
    df_ntd = df_ntd.loc[df_ntd["from_stop_I"] != df_ntd["to_stop_I"]]
    trip_list = list(set(df_ntd["trip_I"].values))
    for trip_id in trip_list:
        seq_numbers = df_ntd[df_ntd["trip_I"] == trip_id]["seq"].values
        new_seq_numbers = [x for _, x in sorted(zip(seq_numbers, range(1,len(seq_numbers)+1)))]
        df_ntd.loc[df_ntd["trip_I"] == trip_id, "seq"] = new_seq_numbers

    df_ntd["from_stop_I"] = [removed_nodes_map[left_stop_id] if left_stop_id in removed_nodes_map else left_stop_id for left_stop_id in df_ntd["from_stop_I"].values]
    df_ntd["to_stop_I"] = [removed_nodes_map[right_stop_id] if right_stop_id in removed_nodes_map else right_stop_id for right_stop_id in df_ntd["to_stop_I"].values]
    
    
    return df_ntd
    
def main():
    logging.getLogger('matplotlib').setLevel(logging.WARNING)

    # If this program is executed from a dataset folder where there's no basis folder, we assume this is an "empty"
    # folder that should be initialized and run the initialization process on the given zip file
    if not os.path.exists("basis"):
        logging.debug("Basis folder doesn't exist, initializing folder from zip file")
        dataset_zip_file = os.path.basename(os.getcwd())+".zip"
        if not os.path.exists(dataset_zip_file):
            logging.error(f"Did not find zip file {dataset_zip_file}.")
            exit(1)
        logging.info("Begin initialization process")
        initialization_process(dataset_zip_file=dataset_zip_file)
        logging.info("End initialization process")

    # At this point the dataset folder for the zip file of the given dataset from the paper exists and the program
    # should be executed from within that folder
    else:
        logging.info("Begin reading configuration")
        config = ConfigReader.read(sys.argv[1])
        logging.info("Finished reading configuration")
        logging.info("Begin reading config parameters")
        write_ptn = config.getBooleanValue("nature_sd_import_write_ptn")
        write_line_concept = config.getBooleanValue("nature_sd_import_write_line_concept")
        write_timetable = config.getBooleanValue("nature_sd_import_write_timetable")
        modes = config.getStringListValue('modalities_all')

        logging.info("Finished reading config parameters")

        
        city_name = os.path.basename(os.getcwd()).split(".")[0]
        # combine stops opposite from one another (and remove possible loops)
        removed_nodes_map = combine_opposite_stops(city_name)
        mode_dict = {"tram": 0, "subway": 1, "rail": 2, "bus": 3, "ferry": 4, "cablecar": 5, "gondola": 6, "funicular": 7}
        for modality in modes:
            subprocess.run(
                [
                    'make',
                    'mm-data',
                    f'modality={modality}',
                ]
            )

            ConfigReader.read(sys.argv[1])
            config = Config.getDefaultConfig()
            if config.getStringValue("modality_category") == "line-based":
                modality_index = mode_dict[modality]
                if write_ptn:
                    logging.info("Begin writing ptn files")
                    create_ptn_files(transportation_mode = modality_index, city_name = city_name, removed_nodes_map=removed_nodes_map, config=config)
                    logging.info("Finished writing ptn files")
                # separate ptn into components
                ptn = PTNReader.read(directed=True)
                conn_components = write_connected_components(ptn, modality_index)
                df_ntd = process_trip_data(modality_index, removed_nodes_map)
                
                if write_line_concept:
                    if not os.path.isdir("./line-planning"):
                        logging.info("Begin creating line-planning folder")
                        os.mkdir("./line-planning")
                        logging.info("Finished creating line-planning folder")
                    logging.info("Begin writing line-concept")
                    # Read the required files
                    df_edges = pd.read_table(config.getStringValue("default_edges_file"), delimiter=";")
                    df_edges.rename(columns={df_edges.columns[0]: df_edges.columns[0].replace("#", "")}, inplace=True)
                    # read line concept
                    old_new_line_dict, new_line_trip_dct, line_complete_df = read_line_information(df_ntd, df_edges)
                    write_lc(line_complete_df, df_ntd, config, old_new_line_dict, new_line_trip_dct, start_time="7:30")
                    linepool = LineReader.read(ptn=ptn, read_costs=False)
                    separate_line_concept_into_components(linepool=linepool, components=conn_components, mode=modality_index)
                    logging.info("Finished writing line-concept")

                if write_timetable:
                    logging.info("Begin writing timetable")
                    logging.debug("Begin making ean")
                    # Read public transport networks
                    ptn = PTNReader.read(directed=True)
                    lines = LineReader.read(ptn=ptn, read_costs=False)

                    # recover needed information about sublines, which were created when making the line concept
                    new_line_trip_df = pd.read_table('./Debug/old_trips_in_network_temp_day_for_sublines.csv', delimiter=";")
                    new_line_trip_dct = dict(zip(new_line_trip_df["(sub-)line id"], new_line_trip_df["trip_id_in_network_temporal_day"]))
                    # change dictionary values which are read in as string (but are really) a list into actual lists
                    for subline, trip_list in new_line_trip_dct.items():
                        new_line_trip_dct[subline] = ast.literal_eval(trip_list)

                    # convert the timetable
                    convert_timetable(df_ntd, config, lines=lines, new_line_trip_dct=new_line_trip_dct, start_time="7:30")
                    logging.debug("Finished making ean")
                    logging.info("Finished writing timetable")
            elif modality == "walk":
                if write_ptn:
                    logging.info("Begin writing ptn files")
                    create_ptn_files(transportation_mode = -1, city_name = city_name, removed_nodes_map=removed_nodes_map, config=config)
                    logging.info("Finished writing ptn files")
            else:
                logging.error(f"Unrecognized modality {modality}, not line-based and not walk")

    return


if __name__ == "__main__":
    main()
