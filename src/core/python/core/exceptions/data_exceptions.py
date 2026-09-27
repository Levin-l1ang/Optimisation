from core.exceptions.exceptions import LinTimException
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from core.model.ptn import Link
    from core.model.infrastructure_network import InfraNode, DirectionType


class DataIndexNotFoundException(LinTimException):
    """
    Exception to throw if an element with a specific index is not found.
    """

    def __init__(self, element: str, index: int):
        """
        Exception to throw if an element with a specific index is not found.

        :param element: type of element which is searched
        :type element: str
        :param index: index of the element
        :type index: str
        """
        super().__init__("Error D3: {} with index {} not found.".format(element, index))


class DataIllegalEventTypeException(LinTimException):
    """
    Exception to throw if the type of an event is undefined.
    """

    def __init__(self, event_id: int, event_type: str):
        """
        Exception to throw if the type of an event is undefined.

        :param event_id: event id
        :type event_id: int
        :param event_type: event type
        :type event_type: str
        """
        super().__init__("Error D4: {} of event {} is no legal event type.".format(event_id, event_type))


class DataIllegalActivityTypeException(LinTimException):
    """
    Exception to throw if the type of an activity is undefined.
    """

    def __init__(self, activity_id: int, activity_type: str):
        """
        Exception to throw if the type of an activity is undefined.

        :param activity_id: activity id
        :type activity_id: int
        :param activity_type: activity type
        :type activity_type: str
        """
        super().__init__("Error D5: {} of activity {} is no legal activity type.".format(activity_type, activity_id))


class DataIllegalLineDirectionException(LinTimException):
    """
    Exception to throw if the direction of an event is undefined.
    """

    def __init__(self, event_id: int, value: str):
        """
        Exception to throw if the direction of an event is undefined.

        :param event_id: event id
        :type event_id: int
        :param value: the false direction value
        :type value: str
        """
        super().__init__("Error D6: {} of event {} is no legal line direction".format(value, event_id))


class DataLinePoolCostInconsistencyException(LinTimException):
    """
    Exception to throw when the number of read line costs does not match the number of lines in the corresponding pool.
    """

    def __init__(self, lines: int, read_line_costs: int, cost_file_name: str):
        """
        Exception to throw when the number of read line costs does not match the number of lines in the corresponding
        pool.

        :param lines: the number of lines in the line pool
        :type lines: int
        :param read_line_costs: the number of read line costs
        :type read_line_costs: int
        :param cost_file_name: the read cost file
        :type cost_file_name: str
        """
        super().__init__("Error D7: Read {} entries in the line cost file {}, but {} lines are in the line pool!"
                         .format(read_line_costs, cost_file_name, lines))


class DataRoutingMultiplePathsException(LinTimException):
    """
    Exception to throw when there are two paths for the same OD-pair in a routing file.
    """

    def __init__(self, node1_id: int, node2_id: int):
        """
        Exception to throw when there are two paths for the same OD-pair in a routing file.

        :param node1_id: id of the first node
        :type node1_id: int
        :param node2_id: id of the second node
        :type node2_id: int
        """
        super().__init__("Error D8: There are multiple paths given from {} to {}!"
                         .format(node1_id, node2_id))


class DataRoutingPathInconsistencyException(LinTimException):
    """
    Exception to throw when there is some inconsistency with a given path in a routing file.
    """

    def __init__(self, node1_id: int, node2_id: int):
        """
        Exception to throw when there is some inconsistency with a given path in a routing file.

        :param node1_id: id of the first node
        :type node1_id: int
        :param node2_id: id of the second node
        :type node2_id: int
        """
        super().__init__("Error D9: The path from {} to {} is not valid!"
                         .format(node1_id, node2_id))


class DataRoutingIncompleteException(LinTimException):
    """
    Exception to throw if the routing is incomplete but a complete routing is necessary.
    """

    def __init__(self):
        """
        Exception to throw if the routing is incomplete but a complete routing is necessary.
        """
        super().__init__("Error D10: The given routing is incomplete, but a complete routing is needed!")


class DataStopInNoZoneException(LinTimException):
    """
    Exception to throw if there is a node not assigned to any zone.
    """

    def __init__(self, stop_id: int):
        """
        Exception to throw if there is a node not assigned to any zone.
        """
        super().__init__("Error D11: The stop {} is not assigned to any zone!".format(stop_id))


class DataStopInMultipleZonesException(LinTimException):
    """
    Exception to throw if a node is assigned to multiple zones.
    """

    def __init__(self, stop_id: int):
        """
        Exception to throw if a node is assigned to multiple zones.
        """
        super().__init__("Error D12: The stop {} is assigned to more than one zone!".format(stop_id))


class DataPriceMatrixNotCompleteException(LinTimException):
    """
    Exception to throw if a price matrix does not contain a price for each non diagonal element.
    """

    def __init__(self, origin_id: int, destination_id: int):
        """
        Exception to throw if a price matrix does not contain a price for each non diagonal element.
        """
        super().__init__("Error D13: There is no price specified from {} to {}".format(origin_id, destination_id))


class DataZonePriceListInconsistencyException(LinTimException):
    """
    Exception to throw if in a zone price file not all prices from 1...n are specified.
    """

    def __init__(self):
        """
        Exception to throw if in a zone price file not all prices from 1...n are specified.
        """
        super().__init__("Error D14: Zone price file is inconsistent.")


class DataRidepoolingAreaNotConnectedException(LinTimException):
    """
    Exception to throw if the edges of a ridepooling area do not form a strongly connected graph.
    """

    def __init__(self, area_id: int):
        """
        Exception to throw if the edges of a ridepooling area do not form a strongly connected graph.
        """
        super().__init__("Error D15: Ridepooling area with id {} is not strongly connected!".format(area_id))


class DataRidepoolingAreaInconsistencyException(LinTimException):
    """
    Exception to throw if there is an inconsistency during the creation of a Ridepooling Area.
    """

    def __init__(self, area_id: int):
        """
        Exception to throw if there is an inconsistency during the creation of a Ridepooling Area.
        """
        super().__init__("Error D16: Ridepooling area with id {} can not be created!".format(area_id))


class DataPTNInfrastructureMapInvalidPathException(LinTimException):
    """
    Exception to throw if the underlying infrastructure information of a PTN edge is not a valid path.
    """

    def __init__(self, ptn_edge_id: int, infra_edge_id: int):
        """
        Exception to throw if the underlying infrastructure information of a PTN edge is not a valid path.
        """
        super().__init__("Error D17: The underlying infrastructure of the PTN edge {} is inconsistent! The infrastructure edge {} can not be added to form a valid path".format(ptn_edge_id, infra_edge_id))


class DataPTNInfrastructureMapStopMismatchException(LinTimException):
    """
    Exception to throw if the underlying infrastructure information of a PTN stop is inconsistent, e.g. the the distance of their coordinates is too large.
    """

    def __init__(self, ptn_stop_id: int, infra_node_id: int):
        """
        Exception to throw if the underlying infrastructure information of a PTN stop is inconsistent, e.g. the the distance of their coordinates is too large.
        """
        super().__init__("Error D18: The underlying infrastructure node of the PTN stop {} is inconsistent! The infrastructure node {} can not be added.\nA stop with the same id as an infrastructure node are considered to belong together, therefore the coordinates must be at most isn_max_distance_coordinates apart. Maybe a scaling issue caused by config key gen_conversion_coordinates".format(ptn_stop_id, infra_node_id))


class DataMultimodalIllegalStopIDException(LinTimException):
    """
    Exception to throw if two stops of different modalities have the same id but the distance of their coordinates is too large.
    """

    def __init__(self, ptn_stop_id: int, modality_1: str, modality_2: str):
        """
        Exception to throw if two stops of different modalities have the same id but the distance of their coordinates is too large.
        """
        super().__init__("Error D19: The modalities {} and {} both have a stop with id {}, but the distance of the coordinates is too large!\nIn a multimodal dataset, stops of different modalities with the same IDs are considered being identical, therefore the coordinates must be at most 2*isn_max_distance_coordinates apart. Maybe a scaling issue caused by config keys <modality>.gen_conversion_coordinates".format(modality_1, modality_2, ptn_stop_id))


class DataPTNInfrastructureMapPathEndpointsMismatchException(LinTimException):
    """
    Exception to throw if the underlying infrastructure information of a PTN edge has different endpoints than the PTN edge itself.
    """

    def __init__(self, ptn_link: 'Link', infra_node_1: 'InfraNode', infra_node_2: 'InfraNode'):
        """
        Exception to throw if the underlying infrastructure information of a PTN edge has different endpoints than the PTN edge itself.
        """
        super().__init__(f"Error D20: The underlying infrastructure of the PTN edge {ptn_link.getId()} has endpoints {ptn_link.getLeftNode().getId()} and {ptn_link.getRightNode().getId()}, but the edge itself has the endpoints {infra_node_1.getId()} and {infra_node_2.getId()}!")


class DataPTNInfrastructureMapDirectionException(LinTimException):
    """
    Exception to throw if the direction of a modality on an infrastructure edge either violates the directedness of this modalities PTN or delivers an inconsistent path in the case of a directed PTN.
    """

    def __init__(self, ptn_link_id: int, modality: str, direction: 'DirectionType', infra_link_id: int):
        """
        Exception to throw if the direction of a modality on an infrastructure edge either violates the directedness of this modalities PTN or delivers an inconsistent path in the case of a directed PTN.
        """
        super().__init__(f"Error D21: The direction {direction.name} of modality {modality} on infrastructure link {infra_link_id} either violates the directedness of this modalities PTN or delivers an inconsistent path for PTN edge {ptn_link_id} in the case of a directed PTN!")


class DataPTNInfrastructureMapModalityException(LinTimException):
    """
    Exception to throw if an infrastructure edge used for the underlying infrastructure of a PTN edge does not support the modality of the PTN edge.
    """

    def __init__(self, modality: str, infra_link_id: int):
        """
        Exception to throw if an infrastructure edge used for the underlying infrastructure of a PTN edge does not support the modality of the PTN edge.
        """
        super().__init__(f"Error D22: The modality {modality} is not supported on infrastructure link {infra_link_id}!")


