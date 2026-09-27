import logging
import sys
import math
import os

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException, ConfigInvalidValueException
from core.io.config import ConfigReader
from core.io.od import ODWriter, ODReader
from core.io.ptn import PTNWriter, PTNReader
from core.io.infrastructure_network import InfrastructureNetworkReader, InfrastructureNetworkWriter
from core.model.ptn import Stop, Link
from core.model.graph import Graph
from core.model.impl.mapOD import MapOD
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.model.infrastructure_network import InfraLink, InfraNode, DirectionType
from random import *
from core.util.mm_utilities import add_modality_prefix
from core.exceptions.exceptions import LinTimException

import networkx as nx

from core.util.networkx import convert_graph_to_networkx
from core.util.multimodal_transfer_method import buildMTGraph

logger = logging.getLogger(__name__)


def node_is_within_radius(node: InfraNode | Stop, center_node: InfraNode | Stop, radius: float, norm: str) -> bool:
    if norm == "euclidean":
        distance = math.sqrt((node.getXCoordinate() - center_node.getXCoordinate()) ** 2 + (node.getYCoordinate() - center_node.getYCoordinate()) ** 2)
    elif norm == "maximum":
        distance = max(abs(node.getXCoordinate() - center_node.getXCoordinate()), abs(node.getYCoordinate() - center_node.getYCoordinate()))
    else:
        raise ConfigInvalidValueException("dg_crop_norm")
    return distance <= radius


if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    logger.info("Start reading configuration")
    config = ConfigReader.read(sys.argv[1])

    ptn_name = config.getStringValue("ptn_name")+"-cropped"
    dataset_is_multimodal = config.getBooleanValue("dg_crop_is_multimodal")
    modalities = config.getStringListValue("dg_crop_modalities")
    center_id = config.getIntegerValue("dg_crop_center_id")
    norm = config.getStringValue("dg_crop_norm")
    radius = config.getDoubleValue("dg_crop_radius")
    logger.info("Finished reading configuration")

    if not dataset_is_multimodal:
        directed = not config.getBooleanValue("ptn_is_undirected")
        if not directed:
            # TODO: run subprocess to directify PTN
            raise LinTimException("PTN must be directed!")
    else:
        for modality in modalities:
            directed = not config.getBooleanValue("ptn_is_undirected", modality)
            if not directed:
                # TODO: run subprocess to directify PTN
                raise LinTimException(f"PTN of modality {modality} is undirected. All PTNs must be directed!")


    logger.info("Begin reading input data")
    if not dataset_is_multimodal:
        od = ODReader.read(MapOD())
        use_stop_coordinates = os.path.isfile(config.getStringValue("default_stops_coordinates_file"))
        ptn: Graph[Stop, Link] = PTNReader.read(read_geo_coordinates=use_stop_coordinates)
    else:
        if len(modalities) == 0:
            logger.error("To crop a multimodal dataset, specify the modalities to crop!")
            raise LinTimException("To crop a multimodal dataset, specify the modalities to crop!")
        use_isn_coordinates = os.path.isfile(config.getStringValue("filename_infrastructure_node_coordinates_file"))
        isn = InfrastructureNetworkReader.read(read_geo_coordinates=use_isn_coordinates)
        od = ODReader.readInfrastructureOd(MapOD())
        ptn: dict[str, Graph[Stop, Link]] = {}
        use_stop_coordinates = {}
        for modality in modalities:
            use_stop_coordinates[modality] = os.path.isfile(add_modality_prefix(modality, config.getStringValue("default_stops_coordinates_file")))
            ptn[modality] = PTNReader.read(modality=modality, read_ptn_infrastructure_map=True, infrastructure_network=isn, read_geo_coordinates=use_stop_coordinates[modality])

    logger.info("Finished reading input data")


    logger.info("Begin cropping dataset")

    if dataset_is_multimodal:
        # extract InfraNode of center_id
        center_node = isn.getNode(center_id)

        # initialize new empty objects for cropped dataset
        isn_cropped = SimpleDictGraph[InfraNode, InfraLink]()
        od_cropped = MapOD()
        ptn_cropped = {modality: SimpleDictGraph[Stop, Link]() for modality in modalities}

        outside_left_nodes: set[InfraNode] = set()      # all isn nodes that are the left node of a link to a node outside the radius
        outside_right_nodes: set[InfraNode] = set()     # all isn nodes that are the right node of a link from a node outside the radius

        logger.info("Cropping infrastructure network")
        # add all nodes and links to cropped ISN that are within the given radius of the center node
        for node in isn.getNodes():
            if node_is_within_radius(node, center_node, radius, norm):
                isn_cropped.addNode(node)
        for link in isn.getEdges():
            if link.getLeftNode() in isn_cropped.getNodes():
                if link.getRightNode() in isn_cropped.getNodes():
                    isn_cropped.addEdge(link)
                else:
                    # mark left node as outside node
                    outside_left_nodes.add(link.getLeftNode())
            else:
                if link.getRightNode() in isn_cropped.getNodes():
                    # mark right node as outside node
                    outside_right_nodes.add(link.getRightNode())

        # add two artificial InfraNodes for passengers from and to outside:
        node_from_outside = InfraNode(max([n.getId() for n in isn.getNodes()]) + 1, "outside_from", max([n.getXCoordinate() for n in isn_cropped.getNodes()]), max([n.getYCoordinate() for n in isn_cropped.getNodes()]), stop_for_modalities=modalities if dataset_is_multimodal else None, latitude=max([n.getLatitude() for n in isn_cropped.getNodes()]), longitude=max([n.getLongitude() for n in isn_cropped.getNodes()]))
        node_to_outside = InfraNode(max([n.getId() for n in isn.getNodes()]) + 2, "outside_to", min([n.getXCoordinate() for n in isn_cropped.getNodes()]), min([n.getYCoordinate() for n in isn_cropped.getNodes()]), stop_for_modalities=modalities if dataset_is_multimodal else None, latitude=min([n.getLatitude() for n in isn_cropped.getNodes()]), longitude=min([n.getLongitude() for n in isn_cropped.getNodes()]))
        isn_cropped.addNode(node_from_outside)
        isn_cropped.addNode(node_to_outside)

        # add link from all outside_left_nodes to outside_to-node
        for outside_node in outside_left_nodes:
            link = InfraLink(max([l.getId() for l in isn_cropped.getEdges()]) + 1, outside_node, node_to_outside, length=0, capacity=math.inf, modalities=outside_node.getModalities(), direction={modality: DirectionType.FORWARDS for modality in outside_node.getModalities()})
            isn_cropped.addEdge(link)

        # add link to all outside_right_nodes from outside_from-node
        for outside_node in outside_right_nodes:
            link = InfraLink(max([l.getId() for l in isn_cropped.getEdges()]) + 1, node_from_outside, outside_node, length=0, capacity=math.inf, modalities=outside_node.getModalities(), direction={modality: DirectionType.FORWARDS for modality in outside_node.getModalities()})
            isn_cropped.addEdge(link)

        # crop infra OD:
        logger.info("Cropping OD matrix")
        isn_cropped_node_ids = [n.getId() for n in isn_cropped.getNodes()]
        for od_pair in od.getODPairs():
            if od_pair.getOrigin() in isn_cropped_node_ids and od_pair.getDestination() in isn_cropped_node_ids:
                od_cropped.setValue(od_pair.getOrigin(), od_pair.getDestination(), od_pair.getValue())
            elif od_pair.getOrigin() in isn_cropped_node_ids:
                od_cropped.setValue(od_pair.getOrigin(), node_to_outside.getId(), od_pair.getValue())
            elif od_pair.getDestination() in isn_cropped_node_ids:
                od_cropped.setValue(node_from_outside.getId(), od_pair.getDestination(), od_pair.getValue())

        isn_node_ids = [node.getId() for node in isn_cropped.getNodes()]

        for modality in modalities:
            logger.info(f"Cropping PTN for modality {modality}")
            for stop in ptn[modality].getNodes():
                if stop.getId() in isn_node_ids:
                    ptn_cropped[modality].addNode(stop)
            ptn_node_ids = [node.getId() for node in ptn_cropped[modality].getNodes()]
            if len(ptn_node_ids) == 0:
                raise LinTimException(f"Cropped PTN for modality {modality} does not contain any stops, choose larger crop_radius!")
            for link in ptn[modality].getEdges():
                if link.getLeftNode().getId() in ptn_node_ids and link.getRightNode().getId() in ptn_node_ids:
                    ptn_cropped[modality].addEdge(link)
            # If at least one Stop of this modality is cropped, add the two artificial outside nodes to the PTN as well
            if len(ptn_cropped[modality].getNodes()) < len(ptn[modality].getNodes()):
                stop_to_outside = Stop(node_to_outside.getId(), node_to_outside.getName(), node_to_outside.getName(), node_to_outside.getXCoordinate(), node_to_outside.getYCoordinate(), latitude=node_to_outside.getLatitude(), longitude=node_to_outside.getLongitude())
                ptn_cropped[modality].addNode(stop_to_outside)
                stop_from_outside = Stop(node_from_outside.getId(), node_from_outside.getName(), node_from_outside.getName(), node_from_outside.getXCoordinate(), node_from_outside.getYCoordinate(), latitude=node_from_outside.getLatitude(), longitude=node_from_outside.getLongitude())
                ptn_cropped[modality].addNode(stop_from_outside)
                for link in ptn[modality].getEdges():
                    # these links do not have underlying infrastructure, since the InfraLinks have infinite capacity anyways
                    if link.getLeftNode() in ptn_cropped[modality].getNodes() and link.getRightNode() not in ptn_cropped[modality].getNodes():
                        ptn_cropped[modality].addEdge(Link(link.getId(), link.getLeftNode(), stop_to_outside, link.getLength(), link.getLowerBound(), link.getUpperBound(), True))
                    elif link.getRightNode() in ptn_cropped[modality].getNodes() and link.getLeftNode() not in ptn_cropped[modality].getNodes():
                        ptn_cropped[modality].addEdge(Link(link.getId(), stop_from_outside, link.getRightNode(), link.getLength(), link.getLowerBound(), link.getUpperBound(), True))

        # remove non connected OD pairs
        passengers_removed = 0

        MTG = buildMTGraph(ptn_cropped.values(), "SIMPLE", 0)
        mtg_nodes_of_stopid = {node.getId(): [] for modality in modalities for node in ptn_cropped[modality].getNodes()}
        for node in MTG.getNodes():
            mtg_nodes_of_stopid[node.getStopId()].append(node.getId())
        ptn_nodes = list(set([n.getId() for modality in modalities for n in ptn_cropped[modality].getNodes()]))

        mtg_nx = convert_graph_to_networkx(MTG, multi_graph=False, weight_function=lambda e: e.getLength())
        for od_pair in od_cropped.getODPairs():
            if not od_pair.getOrigin() in ptn_nodes or not od_pair.getDestination() in ptn_nodes:
                passengers_removed += od_pair.getValue()
                od_cropped.setValue(od_pair.getOrigin(), od_pair.getDestination(), 0)
            elif not nx.has_path(mtg_nx, mtg_nodes_of_stopid[od_pair.getOrigin()][0], mtg_nodes_of_stopid[od_pair.getDestination()][0]):
                passengers_removed += od_pair.getValue()
                od_cropped.setValue(od_pair.getOrigin(), od_pair.getDestination(), 0)

        logger.info(f"Removed {passengers_removed} ({passengers_removed/(od_cropped.computeNumberOfPassengers()+passengers_removed)*100:.2f}%) passengers with non-connected OD pairs")

    else:
        # extract Stop of center_id
        center_node = ptn.getNode(center_id)

        # initialize new empty objects for cropped dataset
        od_cropped = MapOD()
        ptn_cropped = SimpleDictGraph[Stop, Link]()

        outside_left_nodes: set[Stop] = set()      # all ptn stops that are the left node of a link to a node outside the radius
        outside_right_nodes: set[Stop] = set()     # all ptn stops that are the right node of a link from a node outside the radius

        # add all nodes and links to cropped PTN that are within the given radius of the center node
        for node in ptn.getNodes():
            if node_is_within_radius(node, center_node, radius, norm):
                ptn_cropped.addNode(node)

        # add two artificial Stops for passengers from and to outside:
        node_from_outside = Stop(max([n.getId() for n in ptn.getNodes()]) + 1, "outside_from", "outside_from", max([n.getXCoordinate() for n in ptn_cropped.getNodes()]), max([n.getYCoordinate() for n in ptn_cropped.getNodes()]), latitude=max([n.getLatitude() for n in ptn_cropped.getNodes()]), longitude=max([n.getLongitude() for n in ptn_cropped.getNodes()]))
        node_to_outside = Stop(max([n.getId() for n in ptn.getNodes()]) + 2, "outside_to", "outside_to", min([n.getXCoordinate() for n in ptn_cropped.getNodes()]), min([n.getYCoordinate() for n in ptn_cropped.getNodes()]), latitude=min([n.getLatitude() for n in ptn_cropped.getNodes()]), longitude=min([n.getLongitude() for n in ptn_cropped.getNodes()]))
        ptn_cropped.addNode(node_from_outside)
        ptn_cropped.addNode(node_to_outside)

        for link in ptn.getEdges():
            if link.getLeftNode() in ptn_cropped.getNodes():
                if link.getRightNode() in ptn_cropped.getNodes():
                    ptn_cropped.addEdge(link)
                else:
                    ptn_cropped.addEdge(Link(link.getId(), link.getLeftNode(), node_to_outside, link.getLength(), link.getLowerBound(), link.getUpperBound(), True))
            else:
                if link.getRightNode() in ptn_cropped.getNodes():
                    ptn_cropped.addEdge(Link(link.getId(), node_from_outside, link.getRightNode(), link.getLength(), link.getLowerBound(), link.getUpperBound(), True))

        # crop OD:
        logger.info("Cropping OD matrix")
        ptn_node_ids = [node.getId() for node in ptn_cropped.getNodes()]
        for od_pair in od.getODPairs():
            if od_pair.getOrigin() in ptn_node_ids and od_pair.getDestination() in ptn_node_ids:
                od_cropped.setValue(od_pair.getOrigin(), od_pair.getDestination(), od_pair.getValue())
            elif od_pair.getOrigin() in ptn_node_ids:
                od_cropped.setValue(od_pair.getOrigin(), node_to_outside.getId(), od_pair.getValue())
            elif od_pair.getDestination() in ptn_node_ids:
                od_cropped.setValue(node_from_outside.getId(), od_pair.getDestination(), od_pair.getValue())

        # remove non connected OD pairs
        passengers_removed = 0
        ptn_nx = convert_graph_to_networkx(ptn_cropped, multi_graph=False, weight_function=lambda e: e.getLength())
        for od_pair in od_cropped.getODPairs():
            if not nx.has_path(ptn_nx, od_pair.getOrigin(), od_pair.getDestination()):
                passengers_removed += od_pair.getValue()
                od_cropped.setValue(od_pair.getOrigin(), od_pair.getDestination(), 0)

        logger.info(f"Removed {passengers_removed} ({passengers_removed/(od_cropped.computeNumberOfPassengers()+passengers_removed)*100:.2f}%) passengers with non-connected OD pairs")

    logger.info("Finished cropping dataset")


    logger.info("Begin writing output data")
    if not dataset_is_multimodal:
        ODWriter.write(ptn_cropped, od_cropped)
        PTNWriter.write(ptn_cropped, write_geo_coordinates=use_stop_coordinates)
    else:
        InfrastructureNetworkWriter.write(isn_cropped, write_geo_coordinates=use_isn_coordinates)
        ODWriter.writeInfrastructureOd(isn_cropped, od_cropped, write_complete_matrix=False)
        for modality in modalities:
            PTNWriter.write(ptn_cropped[modality], modality=modality, write_ptn_infrastructure_map=True, write_geo_coordinates=use_stop_coordinates[modality])

    # remove all files in basis/ with modality prefix not specified in modalities_to_crop
    if dataset_is_multimodal:
        for filename in os.listdir("./basis/"):
            if len(filename.split("."))>=3:
                if not filename.split(".")[0] in modalities:
                    if filename == "Infrastructure-Node.giv.geo":
                        continue
                    filepath = os.path.join("./basis/", filename)
                    if os.path.isfile(filepath):
                        logger.debug(f"Removing {filepath}")
                        os.remove(filepath)

    # remove After-Config
    logger.debug(f"Removing ./basis/After-Config.cnf")
    os.remove("./basis/After-Config.cnf")

    logger.info("Finished writing output data")
