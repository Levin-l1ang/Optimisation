import logging
import sys
import math

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.exceptions.data_exceptions import DataMultimodalIllegalStopIDException
from core.io.config import ConfigReader
from core.io.ptn import PTNReader, PTNWriter
from core.model.infrastructure_network import InfraNode, InfraLink, DirectionType, InfraPath
from core.io.infrastructure_network import InfrastructureNetworkWriter
from core.model.impl.dict_graph import DictGraph
from core.model.graph import Graph
from core.model.ptn import Stop, Link
from core.model.impl.mapOD import MapOD
from core.io.od import ODReader, ODWriter


logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])
    modalities = config.getStringListValue("modalities_all")
    ptn_is_undirected = {}
    for modality in modalities:
        ptn_is_undirected[modality] = config.getBooleanValue("ptn_is_undirected", modality)
    default_capacity = config.getDoubleValue("isn_default_link_capacity")
    generate_od = config.getBooleanValue("isn_generate_od")
    max_distance_coordinates = config.getDoubleValue("isn_max_distance_coordinates")
    logger.info("Finished reading configuration")


    logger.info("Begin reading input data")
    ptn: dict[str, Graph[Stop, Link]] = {}
    od: dict[str, MapOD] = {}
    for modality in modalities:
        ptn[modality] = PTNReader.read(modality=modality)
        if generate_od:
            od[modality] = ODReader.read(MapOD(), modality=modality)
    logger.info("Finished reading input data")


    logger.info(f"Begin generating infrastructure network from PTNs of modalities {modalities}")

    infraNodeMap: dict[Stop, InfraNode] = {}
    ISN: Graph[InfraNode, InfraLink] = DictGraph()
    for modality in modalities:
        for node in ptn[modality].getNodes():
            # test, if ISN contains already a node with this id:
            existing_node = ISN.getNode(node.getId())
            # test, if the coordinates coincide
            if existing_node:
                if existing_node.getXCoordinate()==node.getXCoordinate() and existing_node.getYCoordinate()==node.getYCoordinate():
                    existing_node.addModality(modality)
                    infraNodeMap[node] = existing_node
                elif max_distance_coordinates > 0:
                    existing_node.addModality(modality)
                    infraNodeMap[node] = existing_node
                    stops = [s for (s,n) in infraNodeMap.items() if n == existing_node]
                    if max([math.sqrt((s1.getXCoordinate() - s2.getXCoordinate())**2 + (s1.getYCoordinate() - s2.getYCoordinate())**2)  for s1 in stops for s2 in stops]) > 2*max_distance_coordinates:
                        raise DataMultimodalIllegalStopIDException(node.getId(), modality, existing_node.getModalities()[0])
                    existing_node.x_coord = sum([s.getXCoordinate() for s in stops])/len(stops)
                    existing_node.y_coord = sum([s.getYCoordinate() for s in stops])/len(stops)
                    logger.debug(f"Coordinates of node {existing_node.getId()} have been averaged")
                else:
                    raise DataMultimodalIllegalStopIDException(node.getId(), modality, existing_node.getModalities()[0])
            else:
                new_node = InfraNode(node.getId(), str(node.getId()), node.getXCoordinate(), node.getYCoordinate(), [modality])
                ISN.addNode(new_node)
                infraNodeMap[node] = new_node


    link_id = 1
    for modality in modalities:
        for edge in ptn[modality].getEdges():
            # only one of those edges should exist in a Infrastructure network, as we assume it is an undirected multigraph, but parallels have either different length or different capacity
            existing_edge_forwards = ISN.get_edge_by_function(lambda x: (x.getLeftNode(), x.getRightNode(), x.getLength(), x.getCapacity()), (infraNodeMap[edge.getLeftNode()], infraNodeMap[edge.getRightNode()], edge.getLength(), default_capacity))
            existing_edge_backwards = ISN.get_edge_by_function(lambda x: (x.getRightNode(), x.getLeftNode(), x.getLength(), x.getCapacity()), (infraNodeMap[edge.getLeftNode()], infraNodeMap[edge.getRightNode()], edge.getLength(), default_capacity))
            path = InfraPath(edge, modality)

            if ptn_is_undirected[modality]:
                if not existing_edge_forwards and not existing_edge_backwards:
                # create new infra link
                    new_link = InfraLink(link_id, infraNodeMap[edge.getLeftNode()], infraNodeMap[edge.getRightNode()], edge.getLength(), capacity=default_capacity, modalities=[modality], direction={modality: DirectionType("BOTH")})
                    ISN.addEdge(new_link)
                    link_id += 1
                    path.addLastEdge(new_link)
                    edge.setUnderlyingInfrastructure(path)
                elif existing_edge_forwards:
                    existing_edge_forwards.addModality(modality, DirectionType("BOTH"))
                    path.addLastEdge(existing_edge_forwards)
                    edge.setUnderlyingInfrastructure(path)
                elif existing_edge_backwards:
                    existing_edge_backwards.addModality(modality, DirectionType("BOTH"))
                    path.addLastEdge(existing_edge_backwards)
                    edge.setUnderlyingInfrastructure(path)
            else: # ptn[modality] is directed
                if not existing_edge_forwards and not existing_edge_backwards:
                # create new infra link
                    new_link = InfraLink(link_id, infraNodeMap[edge.getLeftNode()], infraNodeMap[edge.getRightNode()], edge.getLength(), capacity=default_capacity, modalities=[modality], direction={modality: DirectionType("FORWARDS")})
                    ISN.addEdge(new_link)
                    link_id += 1
                    path.addLastEdge(new_link)
                    edge.setUnderlyingInfrastructure(path)
                elif existing_edge_forwards:
                    if modality in existing_edge_forwards.getModalities():
                        if existing_edge_forwards.getModalityDirection(modality) == DirectionType("BACKWARDS"):
                            existing_edge_forwards.setModalityDirection(modality, DirectionType("BOTH"))
                        elif existing_edge_forwards.getModalityDirection(modality) == DirectionType("FORWARDS"):
                            logger.warning(f"Infra Link with id {existing_edge_forwards.getId()} already contains modality {modality} in FORWARDS direction, but is needed for another edge in the same direction. This should not happen, as we assume that the PTN is a directed multigraph, but it might be caused by parallel edges with equal length and capacity.")
                        path.addLastEdge(existing_edge_forwards)
                        edge.setUnderlyingInfrastructure(path)
                    else:
                        existing_edge_forwards.addModality(modality, DirectionType("FORWARDS"))
                        path.addLastEdge(existing_edge_forwards)
                        edge.setUnderlyingInfrastructure(path)
                elif existing_edge_backwards:
                    if modality in existing_edge_backwards.getModalities():
                        if existing_edge_backwards.getModalityDirection(modality) == DirectionType("FORWARDS"):
                            existing_edge_backwards.setModalityDirection(modality, DirectionType("BOTH"))
                        elif existing_edge_backwards.getModalityDirection(modality) == DirectionType("BACKWARDS"):
                            logger.warning(f"Infra Link with id {existing_edge_backwards.getId()} already contains modality {modality} in BACKWARDS direction, but is needed for another edge in the same direction. This should not happen, as we assume that the PTN is a directed multigraph, but it might be caused by parallel edges with equal length and capacity.")
                        path.addLastEdge(existing_edge_backwards)
                        edge.setUnderlyingInfrastructure(path)
                    else:
                        existing_edge_backwards.addModality(modality, DirectionType("BACKWARDS"))
                        path.addLastEdge(existing_edge_backwards)
                        edge.setUnderlyingInfrastructure(path)

    if generate_od:
        infra_od = MapOD()
        for modality in modalities:
            for origin in ptn[modality].getNodes():
                for destination in ptn[modality].getNodes():
                    infra_od.setValue(infraNodeMap[origin].getId(), infraNodeMap[destination].getId(), infra_od.getValue(infraNodeMap[origin].getId(), infraNodeMap[destination].getId()) + od[modality].getValue(origin.getId(), destination.getId()))

    logger.info("Finished generating infrastructure network")



    logger.info("Begin writing output data")
    InfrastructureNetworkWriter.write(infrastructure_network=ISN)
    if generate_od:
        ODWriter.writeInfrastructureOd(ISN, infra_od)
    for modality in modalities:
        PTNWriter.write(write_links=False, write_stops=False, write_ptn_infrastructure_map=True, modality=modality, ptn=ptn[modality])
    logger.info("Finished writing output data")
