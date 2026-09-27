import logging
import sys
import subprocess
import networkx as nx
import os

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.exceptions.exceptions import LinTimException
from core.io.config import ConfigReader
from core.io.od import ODReader, ODWriter
from core.io.ptn import PTNReader
from core.io.infrastructure_network import InfrastructureNetworkReader
from core.model.impl.mapOD import MapOD
from core.model.ptn import Stop, Link
from core.model.graph import Graph

from core.util.multimodal_transfer_method import buildMTGraph
from core.util.networkx import convert_graph_to_networkx


def closest_node_to_set(G: nx.Graph | nx.DiGraph | nx.MultiGraph | nx.MultiDiGraph, start_nodes: list[int], candidates: list[int], weight="weight"):
    """
    Find a node in candidates which is closest to any node in start_nodes.
    """
    start_nodes = set(start_nodes)
    candidates = set(candidates)

    if weight:
        lengths = dict(nx.multi_source_dijkstra_path_length(
            G, start_nodes, weight=weight
        ))
    else:
        lengths = dict(nx.multi_source_shortest_path_length(
            G, start_nodes
        ))

    reachable = {node: dist for node, dist in lengths.items()
                  if node in candidates}

    if not reachable:
        return None  # no reachable node in candidates

    return min(reachable, key=reachable.get)


logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])

    after_config = config.getStringValue("filename_after_config")

    aggregation_method = config.getStringValue("mm_lpool_od_aggregation")
    use_isn = False
    use_mtg = False
    if aggregation_method.lower() == "use_isn":
        use_isn = True
    elif aggregation_method.lower() == "use_mtg":
        use_mtg = True

    transfer_penalty = config.getDoubleValue('ean_intermodal_change_penalty')
    penalty_algorithm = config.getStringValue('multimodal_transfer_penalty_algorithm')

    modalities = config.getStringListValue("modalities_all")
    modality_category = {}
    for modality in modalities:
        modality_category[modality] = config.getStringValue("modality_category", modality=modality)

    line_modalities = []
    for modality in modalities:
        if modality_category[modality].lower() == "line-based":
            line_modalities.append(modality)
    if len(line_modalities) == 0:
        raise LinTimException("No modality of category line-based given!")

    logger.info("Finished reading configuration")
    logger.info(f"Found {len(line_modalities)} line-based modalities")


    logger.info("Begin reading input data")
    od_infra = ODReader.readInfrastructureOd(MapOD())
    ptn: dict[str, Graph[Stop, Link]] = {}
    isn = None
    if use_isn:
        isn = InfrastructureNetworkReader.read()
    for modality in line_modalities:
        ptn[modality] = PTNReader.read(modality=modality, read_ptn_infrastructure_map=use_isn, infrastructure_network=isn)
    logger.info("Finished reading input data")

    # initialize NX graphs for closest node search
    if use_mtg:
        MTG = buildMTGraph(ptn.values(), penalty_algorithm=penalty_algorithm, transfer_penalty_rate=transfer_penalty)
        nx_search_graph = convert_graph_to_networkx(MTG, False, lambda e: e.getLowerBound())
    if use_isn:
        nx_search_graph = convert_graph_to_networkx(isn, True, lambda e: e.getLength())


    logger.info("Begin generating line pools for all line-based modalities")
    for modality in line_modalities:

        if use_isn or use_mtg:
            nx_ptn = convert_graph_to_networkx(ptn[modality], multi_graph=False, weight_function=lambda x: 1)
            ignored_passengers = 0

            # initialize subsets of nodes of NX graph for closest node search
            if use_mtg:
                nx_node_subset = [s.getId() for s in MTG.getNodes() if s.getModality()==modality]
            if use_isn:
                nx_node_subset = [s.getId() for s in ptn[modality].getNodes()]

            logger.info(f"Begin constructing OD Matrix for modality {modality}")
            od_modality = MapOD()
            for od_pair_infra in od_infra.getODPairs():

                # try if od.origin is in ptn of this modality
                try:
                    org_modality = ptn[modality].getNode(od_pair_infra.getOrigin())
                    org_modality_id = org_modality.getId()
                except KeyError:
                    # determine closest stop to the origin of the od pair
                    if use_mtg:
                        # all nodes corresponding to this ISN node in MTG:
                        start_nodes = [n.getId() for n in MTG.getNodes() if n.getStopId()==od_pair_infra.getOrigin()]
                        # for an MTG we have to convert back to the actual stop id
                        org_modality_id = MTG.getNode(closest_node_to_set(nx_search_graph, start_nodes, nx_node_subset)).getStopId()
                    if use_isn:
                        start_nodes = [od_pair_infra.getOrigin()]
                        org_modality_id = closest_node_to_set(nx_search_graph, start_nodes, nx_node_subset)

                # try if od.destination is in ptn of this modality
                try:
                    dest_modality = ptn[modality].getNode(od_pair_infra.getDestination())
                    dest_modality_id = dest_modality.getId()
                except KeyError:
                    # determine closest stop to the destination of the od pair
                    if use_mtg:
                        # all nodes corresponding to this ISN node in MTG:
                        start_nodes = [n.getId() for n in MTG.getNodes() if n.getStopId()==od_pair_infra.getDestination()]
                        # for an MTG we have to convert back to the actual stop id
                        dest_modality_id = MTG.getNode(closest_node_to_set(nx_search_graph, start_nodes, nx_node_subset)).getStopId()
                    if use_isn:
                        start_nodes = [od_pair_infra.getDestination()]
                        dest_modality_id = closest_node_to_set(nx_search_graph, start_nodes, nx_node_subset)

                if org_modality_id is None or dest_modality_id is None:
                    logger.debug(f"ISN-OD pair {od_pair_infra.getOrigin()} to {od_pair_infra.getDestination()} with {od_pair_infra.getValue()} passengers cannot be mapped to a stop in modality {modality}. Ignore them.")
                    ignored_passengers += od_pair_infra.getValue()
                    continue
                if org_modality_id != dest_modality_id:
                    # test if there is a path for this OD pair in the PTN of this modality. If not, load generation fails
                    if not nx.has_path(nx_ptn,org_modality_id, dest_modality_id):
                        logger.debug(f"No path for ISN-OD pair {od_pair_infra.getOrigin()} to {od_pair_infra.getDestination()} with {od_pair_infra.getValue()}. Ignore them.")
                        ignored_passengers += od_pair_infra.getValue()
                    else:
                        od_modality.setValue(org_modality_id, dest_modality_id, od_modality.getValue(org_modality_id, dest_modality_id)+od_pair_infra.getValue())
                else:
                    logger.debug(f"ISN-OD pair {od_pair_infra.getOrigin()} to {od_pair_infra.getDestination()} with {od_pair_infra.getValue()} passengers was mapped to the same origin and destination stop. Ignore them.")
                    ignored_passengers += od_pair_infra.getValue()
            logger.info(f"{ignored_passengers} passengers ({ignored_passengers/od_infra.computeNumberOfPassengers()*100:.2f}%) ignored due to non-connectivity in modality {modality}")

            logger.info(f"Finished constructing OD Matrix for modality {modality}")


            logger.info(f"Begin writing temporary OD Matrix for modality {modality}")
            # Create temporary config file
            tmp_config = "Temp-Config.cnf"
            with open(f"basis/{tmp_config}", 'w') as tmp:
                tmp.write(f"default_od_file; basis/{modality}.OD-tmp.giv\n")

            # tell after config to read temp config
            include_line = f"include_if_exists; \"{tmp_config}\"\n"
            if os.path.exists(after_config):
                with open(after_config, 'a') as f:
                    f.write(include_line)
            else:
                with open(after_config, 'w') as f:
                    f.write(include_line)
            ODWriter.write(ptn[modality], od_modality, modality=modality, write_complete_matrix=False, file_name=f"basis/{modality}.OD-tmp.giv")
            logger.info(f"Finished writing temporary OD Matrix for modality {modality}")


        logger.info(f"Run subrocess make mm-data modality={modality}")
        subprocess.run(['make', 'mm-data', f'modality={modality}'])
        logger.info(f"Begin generating PTN loads and line pool for modality {modality}")
        logger.info(f"Run subrocess make ptn-regenerate-load for modality {modality}")
        subprocess.run(['make', 'ptn-regenerate-load'])
        logger.info(f"Run subrocess make lpool-line-pool for modality {modality}")
        subprocess.run(['make', 'lpool-line-pool'])
        logger.info(f"Finished generating PTN loads and line pool for modality {modality}")
        subprocess.run(['make', 'mm-exit'])
        os.remove(f"basis/{modality}.Load.giv")

        if use_isn or use_mtg:
            os.remove(f"basis/{modality}.OD-tmp.giv")
            os.remove(f"basis/{tmp_config}") # Remove temporary config

    logger.info("Finished generating line pools for all line-based modalities")
