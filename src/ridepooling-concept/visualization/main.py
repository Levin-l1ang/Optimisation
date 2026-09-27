import logging
import sys
import matplotlib.pyplot as plt
from typing import Dict
import networkx as nx
from pathlib import Path

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.exceptions.exceptions import LinTimException
from core.io.infrastructure_network import InfrastructureNetworkReader
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.io.ridepooling import RidepoolingPoolReader
from core.io.lines import LineReader
from core.model.lines import LinePool
from core.model.ptn import Link, Stop
from core.model.graph import Graph
from core.model.ridepooling import RidepoolingPool
from core.model.infrastructure_network import InfraLink, InfraNode
from core.util.config import Config

logger = logging.getLogger(__name__)

def draw_graph(percentage_ridepool: Dict[InfraLink, float], config: Config, isn: Graph[InfraNode, InfraLink], rc_draw_conversion_factor: float) -> None:

    logger.debug("Begin reading graph-specific config parameters")
    filename_rplp_file = config.getStringValueStatic("filename_rplp_file")
    logger.debug("Finished reading graph-specific config parameters")

    # create network x graph
    nx_rplp_graph = nx.MultiGraph()

    # prepare the nodes
    for node in isn.getNodes():
        node_id = node.getId()
        nx_rplp_graph.add_node(node_id)
        nx_rplp_graph.nodes[node_id]["position"] = [node.getXCoordinate(), node.getYCoordinate()]

    for edge in isn.getEdges():
        u = edge.getLeftNode().getId()
        v = edge.getRightNode().getId()
        nx_rplp_graph.add_edge(u, v, key=edge.getId())

        # add rp percantage as attribute
        nx_rplp_graph[u][v][edge.getId()]["rp_percentage"] = percentage_ridepool[edge]


    nodes = [node.getId() for node in isn.getNodes()]

    # set node attributes to use for drawing
    pos = nx.get_node_attributes(nx_rplp_graph, "position")
    labels = dict(zip(nodes, nodes))

    connection_style = "arc3"

    # draw the network x graph
    logger.info("Begin making graph figure")
    logging.disable(10)
    plt.figure()

    # set up attributes for graph
    cmap = plt.cm.binary
    # draw graph
    nx.draw_networkx_nodes(nx_rplp_graph, pos=pos, node_color="w", node_shape='o',
                            node_size=300 /rc_draw_conversion_factor)
    nx.draw_networkx_labels(nx_rplp_graph, pos=pos, labels=labels, font_size=12 / rc_draw_conversion_factor)

    edge_list, color_weights = zip(*nx.get_edge_attributes(nx_rplp_graph, 'rp_percentage').items())
    nx.draw_networkx_edges(nx_rplp_graph, pos=pos, edgelist=edge_list, connectionstyle=connection_style,
                            width=2/rc_draw_conversion_factor, edge_color=color_weights, edge_cmap=cmap, edge_vmin=0)

    # set legend
    vmax = 1.0
    ax = plt.gca()
    ax.set_aspect('equal')
    sm = plt.cm.ScalarMappable(cmap=cmap, norm=plt.Normalize(vmin=0, vmax=vmax))
    sm._A = []
    cbar = plt.colorbar(sm, ax=ax) #, fraction=0.046, pad=0.04)
    cbar.set_label("Ridepooling Percentage")  # rotation = 270

    # make outline around nodes

    ax.collections[0].set_edgecolor("#000000")
    # remove the frame around the figure
    ax.axis('off')
    logger.info("Finished making graph figure")

    # saving the figure
    logger.info("Begin saving RPLP graph")
    Path(filename_rplp_file).parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(filename_rplp_file, dpi=600, facecolor="white")
    logging.disable(0)
    logger.info("Finished saving RPLP graph")
    return





if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])

    modalities = config.getStringListValue("modalities_all")
    modality_category = {}
    vehicle_capacity = {}
    for modality in modalities:
        modality_category[modality] = config.getStringValue("modality_category", modality=modality)
        vehicle_capacity[modality] = config.getIntegerValue("gen_passengers_per_vehicle", modality=modality)

    rpool_modalities = []
    line_modalities = []
    for modality in modalities:
        if modality_category[modality].lower() == "line-based":
            line_modalities.append(modality)
        elif modality_category[modality].lower() == "ridepooling":
            rpool_modalities.append(modality)

    if len(rpool_modalities) == 0 or len(line_modalities) == 0:
        raise LinTimException("At least one modality of category line-based and at least one of category ridepooling must be given in modalities_all!")

    rc_draw_coordinate_factor = config.getDoubleValue("rc_rp_percentage_draw_coordinate_factor")
    logger.info("Finished reading configuration")


    logger.info("Begin reading input data")
    isn = InfrastructureNetworkReader.read()

    ptn: dict[str, Graph[Stop, Link]] = {}
    lconcept: dict[str, LinePool] = {}
    rconcept: dict[str, RidepoolingPool] = {}
    for modality in line_modalities:
        ptn[modality] = PTNReader.read(modality=modality, read_ptn_infrastructure_map=True, infrastructure_network=isn)
        lconcept[modality] = LineReader.read(ptn[modality], read_frequencies=True, modality=modality)
    for modality in rpool_modalities:
        ptn[modality] = PTNReader.read(modality=modality, read_ptn_infrastructure_map=True, infrastructure_network=isn)
        rconcept[modality] = RidepoolingPoolReader.read(ptn[modality], read_number_vehicles=True, read_vehicle_frequencies=True, modality=modality)

    logger.info("Finished reading input data")


    logger.info("Begin computing ridepooling percentages")

    rp_cap = {}
    line_cap = {}
    for link in isn.getEdges():
        rp_cap[link] = 0
        line_cap[link] = 0

    for modality in line_modalities:
        for line in lconcept[modality].getLineConcept():
            for edge in line.getLinePath().getEdges():
                for link in edge.getUnderlyingInfrastructure().getEdges():
                    line_cap[link] += line.getFrequency() * vehicle_capacity[modality]
    for modality in rpool_modalities:
        for area in rconcept[modality].getAreas():
            for edge in area.getEdges():
                for link in edge.getUnderlyingInfrastructure().getEdges():
                    rp_cap[link] += area.getVehicleFrequency(edge.getId()) * area.getNumberOfVehicles() * vehicle_capacity[modality]

    percentage_ridepool = {}
    for link in isn.getEdges():
        if rp_cap[link] + line_cap[link] == 0:
            percentage_ridepool[link] = 0
        else:
            percentage_ridepool[link] = rp_cap[link] / (rp_cap[link] + line_cap[link])

    draw_graph(percentage_ridepool, config, isn, rc_draw_coordinate_factor)





