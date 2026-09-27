import logging
import sys
import random

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.exceptions.input_exceptions import InputFileException, InputUnsupportedModalityCategoryException
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.model.ridepooling import RidepoolingPool, RidepoolingArea
from core.io.ridepooling import RidepoolingPoolWriter, RidepoolingPoolReader
from core.io.infrastructure_network import InfrastructureNetworkReader

logger = logging.getLogger(__name__)



if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])

    modality_category = config.getStringValue("modality_category")
    if modality_category.lower() != "ridepooling":
        logger.error("Can only create a ridepooling pool, if modality is of modality category ridepooling!")
        raise InputUnsupportedModalityCategoryException(modality_category)

    cost = config.getDoubleValue("rpool_costs_fixed")
    period_length = config.getIntegerValue("period_length")
    vehicle_capacity = config.getIntegerValue("gen_passengers_per_vehicle")
    min_edges = config.getIntegerValue("rpool_min_edges")
    max_edges = config.getIntegerValue("rpool_max_edges")
    min_nodes = config.getIntegerValue("rpool_induced_min_nodes")
    max_nodes = config.getIntegerValue("rpool_induced_max_nodes")
    compute_distribution = config.getBooleanValue("rpool_compute_vehicle_frequencies")
    extend_rpool = config.getBooleanValue("rpool_extend_existing_pool")
    write_stretch = config.getBooleanValue("rpool_write_stretch")
    use_ptn_load= config.getBooleanValue("rpool_use_ptn_load")
    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    if not use_ptn_load:
        isn = InfrastructureNetworkReader.read(read_loads=True)
        ptn = PTNReader.read(read_loads=True, read_ptn_infrastructure_map=True, infrastructure_network=isn)
    else:
        ptn = PTNReader.read(read_loads=True)

    if extend_rpool:
        try:
            rpool = RidepoolingPoolReader.read(ptn, read_number_vehicles=False, read_vehicle_frequencies=True)
        except InputFileException:
            logger.error("Impossible to extend a ridepooling pool without an existing one! Program assumes rpool_extend_existing_pool to false.")
            rpool = RidepoolingPool(cost)
    else:
        rpool = RidepoolingPool(cost)
    logger.info("Finished reading input data")

    logger.info("Begin computing ridepooling pool")
    if max_nodes == -1:
        max_nodes = len(ptn.getNodes())
    if max_edges == -1:
        max_edges = len(ptn.getEdges())

    idx = rpool.getNextAreaId()

    for node in ptn.getNodes():
        node_set = [node]
        while len(node_set) < max_nodes:
            existing_node = random.choice(node_set)
            new_node = random.choice(ptn.getAdjacentNodes(existing_node))
            node_set.append(new_node)
            node_set = list(set(node_set))
            if len(node_set) >= min_nodes:
                area = RidepoolingArea(idx)
                for node1 in node_set:
                    for node2 in node_set:
                        if node1!=node2:
                            edge = ptn.get_edge_by_nodes(node1, node2)
                            if edge:
                                area.addLink(edge)
                if (len(area.getEdges())>=min_edges and len(area.getEdges())<=max_edges):
                    if compute_distribution:
                        area.computeVehicleFrequenciesByEulerCircle(period_length, vehicle_capacity, use_ptn_load)
                    success = rpool.addArea(area)
                    idx = rpool.getNextAreaId()

    # This was a try to make the algorithm deterministic. Not successfull yet!

    #nx_graph = convert_graph_to_networkx(ptn, False, lambda x: 1)

    #node_ids = [n.getId() for n in ptn.getNodes()]

    #for nb_nodes in range(min_nodes, max_nodes+1):
    #    logger.info(f"Creating induced subgraphs with {nb_nodes} nodes...")#

    #    for node_comb in combinations(node_ids, nb_nodes):
    #        subgraph = nx_graph.subgraph(node_comb)
    #        if nx.is_connected(subgraph):
    #            area = RidepoolingArea(idx, edges=[ptn.get_edge_by_nodes(ptn.getNode(l), ptn.getNode(r)) for (l,r) in subgraph.edges])
    #            if not use_edge_bounds or (len(area.getEdges())>=min_edges and len(area.getEdges())<=max_edges):
    #                success = rpool.addArea(area)
    #                if success:
    #                    idx += 1


    logger.info("Finished computing ridepooling pool")

    logger.info("Begin writing output data")
    if compute_distribution:
        RidepoolingPoolWriter.write(rpool, write_ride_concept=False, write_distribution=True, write_stretch_factors=write_stretch)
    else:
        RidepoolingPoolWriter.write(rpool, write_ride_concept=False, write_distribution=False, write_stretch_factors=write_stretch)
    logger.info("Finished writing output data")


