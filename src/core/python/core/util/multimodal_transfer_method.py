from core.model.multimodal_transfer import MTType, MTEdge, MTNode
from core.model.graph import Graph
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.model.ptn import Link, Stop


def get_transfer_penalty(
    transfer_penalty_rate: int,
    penalty_algorithm: str,
) -> int:
    """
    Get the transfer penalty value based on the given penalty algorithm and rate.
    :param transfer_penalty_rate: penalty parameter for a transfer
    :param penalty_algorithm: String specifying the penalty algorithm used for
        transfer edges penalties.
    :return: the transfer penalty value
    """
    if penalty_algorithm == "SIMPLE":  # TODO: Add more algorithms if needed
        return transfer_penalty_rate
    else:
        raise RuntimeError("Invalid transfer penalty algorithm!")


def buildMTGraph(
    graphs: list[Graph[Stop, Link]],
    penalty_algorithm: str,
    transfer_penalty_rate: int,
) -> Graph[MTNode, MTEdge]:
    """
    Builds an Multimodal Transfer graph, given a multimodal PTN
    and the transfer penalty models. The length of the edges will depend on the
    models used.

    :param graphs: The list of PTN graphs to join to the the multimodal network
    :param penalty_algorithm: String specifying the penalty algorithm used for
        transfer edges penalties.
    :param transfer_penalty_rate: penalty parameter for a transfer
    :return: a Multimodal Transfer Graph
    """

    # Check if there's a mixture of directed/undirected graphs: that is, not all of the graphs are directed but some of them are
    if (not all(graph.isDirected() for graph in graphs)) and (any(graph.isDirected() for graph in graphs)): 
        raise RuntimeError('Set of graphs contains both directed and undirected graphs, this is not currently supported.')
    transfer_penalty_value = get_transfer_penalty(
        transfer_penalty_rate,
        penalty_algorithm,
    )

    joint_graph = SimpleDictGraph()
    running_node_id = 0
    running_edge_id = 0
    stop_to_nodes_map: dict[int, list[MTNode]] = {}

    # include all PTNs as subgraphs
    for graph in graphs:
        id_map: dict[int, MTNode] = {}
        for node in graph.getNodes():
            running_node_id += 1
            node = MTNode(
                node_id=running_node_id,
                stopId=node.getId(),
                shortName=node.getShortName(),
                longName=node.getLongName(),
                xCoordinate=node.getXCoordinate(),
                yCoordinate=node.getYCoordinate(),
                latitude=node.getLatitude(),
                longitude=node.getLongitude(),
                modality=node.getModality(),
            )
            joint_graph.addNode(node)
            id_map[node.stop_id] = node
            stop_to_nodes_map.setdefault(node.stop_id, []).append(node)
        for edge in graph.getEdges():
            running_edge_id += 1
            edge = MTEdge(
                link_id=running_edge_id,
                left_stop=id_map[edge.left_stop.getId()],
                right_stop=id_map[edge.right_stop.getId()],
                edge_type=MTType.LINE,
                length=edge.getLength(),
                lower_bound=edge.getLowerBound(),
                upper_bound=edge.getUpperBound(),
                directed=edge.directed
            )
            joint_graph.addEdge(edge)

    # add transfer nodes and edges
    for stop_id, nodes in stop_to_nodes_map.items():
        # only add transfer nodes if there are multiple modalities at the same stop
        if len(nodes) == 1:
            continue
        running_node_id += 1
        transfer_node = MTNode(
            node_id=running_node_id,
            stopId=stop_id,
            shortName=nodes[0].getShortName(),
            longName=nodes[0].getLongName(),
            xCoordinate=nodes[0].getXCoordinate(),
            yCoordinate=nodes[0].getYCoordinate(),
            latitude=nodes[0].getLatitude(),
            longitude=nodes[0].getLongitude(),
            modality='',
        )
        joint_graph.addNode(transfer_node)
        # connect all nodes at this stop to the transfer node
        for node in nodes:
            running_edge_id += 1
            transfer_edge = MTEdge(
                link_id=running_edge_id,
                left_stop=node,
                right_stop=transfer_node,
                edge_type=MTType.TRANSFER,
                length=0,
                lower_bound=transfer_penalty_value,
                upper_bound=transfer_penalty_value,
                directed=joint_graph.isDirected(),
            )
            joint_graph.addEdge(transfer_edge)
            if joint_graph.isDirected():
                running_edge_id += 1
                transfer_edge = MTEdge(
                    link_id=running_edge_id,
                    left_stop=transfer_node,
                    right_stop=node,
                    edge_type=MTType.TRANSFER,
                    length=0,
                    lower_bound=transfer_penalty_value,
                    upper_bound=transfer_penalty_value,
                    directed=joint_graph.isDirected(),
                )
                joint_graph.addEdge(transfer_edge)
    return joint_graph
