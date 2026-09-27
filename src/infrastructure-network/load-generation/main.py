import logging
import sys
import math

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.io.od import ODReader
from core.io.infrastructure_network import InfrastructureNetworkReader, InfrastructureNetworkWriter
from core.algorithm.dijkstra_multi import Dijkstra
from core.model.infrastructure_network import InfraLink, InfraNode
from core.model.impl.mapOD import MapOD
from core.model.impl.dict_graph import DictGraph
from core.model.graph import Edge

logger = logging.getLogger(__name__)

class RoutingEdge(Edge[InfraNode]):
    """Implementing a directed routing edge, used to calculate the load as maximum of loads in both directions.
    """

    def __init__(self, link_id: int, left_node: InfraNode, right_node: InfraNode, length: float, directed: bool, load: float = 0) -> None:
        self.link_id = link_id
        self.left_node = left_node
        self.right_node = right_node
        self.length = length
        self.directed = directed
        self.load = load

    def getLeftNode(self) -> InfraNode:
        return self.left_node

    def getRightNode(self) -> InfraNode:
        return self.right_node

    def getId(self) -> int:
        return self.link_id

    def setId(self, id: int) -> None:
        self.link_id = id

    def isDirected(self) -> bool:
        return self.directed

    def getLength(self) -> float:
        return self.length

    def getLoad(self) -> float:
        return self.load

    def setLoad(self, load: float) -> None:
        self.load = load



if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])
    modalities = config.getStringListValue("modalities_all")
    logger.info("Finished reading configuration")


    logger.info("Begin reading input data")
    isn = InfrastructureNetworkReader.read()
    od = ODReader.readInfrastructureOd(MapOD())
    logger.info("Finished reading input data")


    logger.info("Begin computing load on infrastructure network")

    # Remove infrastructure links, that have no modality listed in modalities_all
    for link in isn.getEdges():
        remove = True
        for modality in link.getModalities():
            if modality in modalities:
                remove = False
        if remove:
            # do not remove edge, otherwise nodes may also be deleted. Set Length to infinity to make sure it is never used
            link.length = math.inf

    # transform graph to directed graph:
    routing_graph = DictGraph()
    num_isn_edges = len(isn.getEdges())

    for node in isn.getNodes():
        routing_graph.addNode(node)
    for link in isn.getEdges():
        forwards = RoutingEdge(link.getId(), link.getLeftNode(), link.getRightNode(), link.getLength(), directed=True)
        routing_graph.addEdge(forwards)
        backwards = RoutingEdge(link.getId()+num_isn_edges, link.getRightNode(), link.getLeftNode(), link.getLength(), directed=True)
        routing_graph.addEdge(backwards)


    skipped_passengers = 0
    all_passengers = od.computeNumberOfPassengers()

    for origin in routing_graph.getNodes():
        dijkstra = Dijkstra(routing_graph, origin, InfraLink.getLength)
        dijkstra.computeShortestPaths()
        for destination in routing_graph.getNodes():
            if origin != destination:
                path = dijkstra.getPath(destination)
                if not path:
                    skipped_passengers += od.getValue(origin.getId(), destination.getId())
                    logger.debug(f"No path from node {origin.getId()} to node {destination.getId()}, {od.getValue(origin.getId(), destination.getId())} passengers are skipped.")
                else:
                    for link in path.getEdges():
                        routing_graph.getEdge(link.getId()).setLoad(link.getLoad() + od.getValue(origin.getId(), destination.getId()))

    # set load on undirected edge to maximum of loads in both directions:
    for link in isn.getEdges():
        link.setLoad(max(routing_graph.getEdge(link.getId()).getLoad(), routing_graph.getEdge(link.getId()+num_isn_edges).getLoad()))

    logger.info("Finished computing load on infrastructure network")

    if skipped_passengers > 0:
        logger.warning(f"{skipped_passengers} passengers ({round(skipped_passengers/all_passengers*100,2)}% of all passengers) cannot be routed and are ignored, as the corresponding infrastructure nodes are not connected.")

    logger.info("Begin writing output data")
    InfrastructureNetworkWriter.write(isn, write_infrastructure_links=False, write_infrastructure_nodes=False, write_load=True)
    logger.info("Finished writing output data")
