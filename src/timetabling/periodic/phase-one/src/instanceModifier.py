import sys
import logging

from core.model.graph import Graph
from core.model.periodic_ean import PeriodicEvent, PeriodicActivity
from core.model.activityType import ActivityType
from core.model.eventType import EventType

from core.model.impl.dict_graph import DictGraph
from event_activity_network import PeriodicEventActivityNetwork

from helper import Parameters

logger = logging.getLogger(__name__)


class InstanceModifier:
    @staticmethod
    def create_extended_ean(ean: Graph[PeriodicEvent, PeriodicActivity], parameters: Parameters) \
            -> Graph[PeriodicEvent, PeriodicActivity]:

        if parameters.tim_phase_one_extend_ean_model == "full":
            return InstanceModifier.variant_full(ean, parameters)
        elif parameters.tim_phase_one_extend_ean_model == "cycle_base":
            return InstanceModifier.variant_cycle_base(ean, parameters)
        elif parameters.tim_phase_one_extend_ean_model == "minimal":
            return InstanceModifier.variant_minimal(ean, parameters)
        else:
            logger.error("No function for tim_phase_one_extended_ean_model")
            sys.exit(1)

    @staticmethod
    def variant_minimal(original_ean: Graph[PeriodicEvent, PeriodicActivity], parameters: Parameters) \
            -> Graph[PeriodicEvent, PeriodicActivity]:

        for activity in original_ean.getEdges():
            if activity.getUpperBound() - activity.getLowerBound() >= parameters.period_length - 1:
                original_ean.removeEdge(activity)
                # TODO: Problem: activity is removed in the original ean object

        return InstanceModifier.variant_cycle_base(original_ean=original_ean, parameters=parameters)

    @staticmethod
    def variant_cycle_base(original_ean: Graph[PeriodicEvent, PeriodicActivity], parameters: Parameters) \
            -> Graph[PeriodicEvent, PeriodicActivity]:
        # TODO: also with given tree?
        # TODO: different weight functions?

        model_ean = PeriodicEventActivityNetwork(graph=original_ean, period_length=parameters.period_length,
                                                 weight_function=lambda x: (x.getUpperBound() - x.getLowerBound()))
        model_ean.calculate_nx_graph()
        model_ean.calculate_nx_spanning_tree()
        model_ean.calculate_cycle_base()

        # adding nodes and edges to the new ean
        ean = DictGraph()

        max_node_id = max([n.getId() for n in original_ean.getNodes()])
        max_edge_id = max([e.getId() for e in original_ean.getEdges()])

        # add original events
        for event in original_ean.getNodes():
            org_event = PeriodicEvent(event_id=event.getId(),
                                      stop_id=event.getStopId(),
                                      event_type=event.getType(),
                                      line_id=event.getLineId(),
                                      time=-1,
                                      number_of_passengers=event.getNumberOfPassengers(),
                                      direction=event.getDirection(),
                                      line_frequency_repetition=event.getLineFrequencyRepetition())

            ean.addNode(org_event)

        # add events
        activities_original = [e for e in model_ean.graph.getEdges() if e not in model_ean.network_matrix_dict.keys()]
        activities_to_new = [e for e in model_ean.network_matrix_dict.keys()]

        for activity in activities_original:
            org_activity = PeriodicActivity(activityId=activity.getId(),
                                            activityType=activity.getType(),
                                            sourceEvent=ean.getNode(activity.getLeftNode().getId()),
                                            targetEvent=ean.getNode(activity.getRightNode().getId()),
                                            lowerBound=activity.getLowerBound(),
                                            upperBound=activity.getUpperBound(),
                                            numberOfPassengers=0)
            ean.addEdge(org_activity)

        for activity in activities_to_new:
            left_node = ean.getNode(activity.getLeftNode().getId())
            right_node = ean.getNode(activity.getRightNode().getId())

            max_node_id += 1
            new_event = PeriodicEvent(event_id=max_node_id,
                                      stop_id=right_node.stop_id,
                                      event_type=EventType.VIRTUAL,
                                      line_id=right_node.line_id,
                                      time=-1,
                                      number_of_passengers=right_node.number_of_passengers,
                                      direction=right_node.direction,
                                      line_frequency_repetition=right_node.line_frequency_repetition)

            ean.addNode(new_event)

            old_activity = PeriodicActivity(activityId=activity.getId(),
                                            activityType=activity.getType(),
                                            sourceEvent=left_node,
                                            targetEvent=new_event,
                                            lowerBound=activity.getLowerBound(),
                                            upperBound=activity.getUpperBound(),
                                            numberOfPassengers=0)

            max_edge_id += 1
            virtual_activity = PeriodicActivity(activityId=max_edge_id,
                                                activityType=ActivityType.VIRTUAL,
                                                sourceEvent=new_event,
                                                targetEvent=right_node,
                                                lowerBound=0,
                                                upperBound=parameters.period_length - 1,
                                                numberOfPassengers=1)

            ean.addEdge(old_activity)
            ean.addEdge(virtual_activity)

        ean.orderEdges(lambda x: x.getId())

        # calculate the times for the feasible timetable
        used = {node: False for node in ean.getNodes()}

        for node in used.keys():
            if used[node]:
                continue
            node.setTime(0)
            used[node] = True
            InstanceModifier.bfs(ean, node, used, parameters.period_length)

        return ean

    @staticmethod
    def bfs(ean: Graph[PeriodicEvent, PeriodicActivity], node: PeriodicEvent, used: dict, period_length: int):
        queue = [node]
        while len(queue) > 0:
            cur = queue.pop(0)

            for edge in ean.getIncidentEdges(cur):
                if edge.getType() == ActivityType.VIRTUAL:
                    continue

                if edge.getLeftNode() == cur:
                    next_node = edge.getRightNode()
                    if not used[next_node]:
                        queue.append(next_node)
                        used[next_node] = True
                        next_node.setTime(int((cur.time + edge.getLowerBound()) % period_length))
                else:
                    next_node = edge.getLeftNode()
                    if not used[next_node]:
                        queue.append(next_node)
                        used[next_node] = True
                        next_node.setTime(int((cur.time - edge.getLowerBound()) % period_length))

    @staticmethod
    def variant_full(original_ean: Graph[PeriodicEvent, PeriodicActivity], parameters: Parameters) \
            -> Graph[PeriodicEvent, PeriodicActivity]:
        ean = DictGraph()

        max_node_id = max([n.getId() for n in original_ean.getNodes()])
        max_edge_id = max([e.getId() for e in original_ean.getEdges()])

        # add existing events
        for event in original_ean.getNodes():
            org_event = PeriodicEvent(event_id=event.getId(),
                                      stop_id=event.getStopId(),
                                      event_type=event.getType(),
                                      line_id=event.getLineId(),
                                      time=0,
                                      number_of_passengers=event.getNumberOfPassengers(),
                                      direction=event.getDirection(),
                                      line_frequency_repetition=event.getLineFrequencyRepetition())

            ean.addNode(org_event)

        for activity in original_ean.getEdges():
            left_node = ean.getNode(activity.getLeftNode().getId())
            right_node = ean.getNode(activity.getRightNode().getId())

            # add new virtual event
            max_node_id += 1
            new_event = PeriodicEvent(event_id=max_node_id,
                                      stop_id=right_node.stop_id,
                                      event_type=EventType.VIRTUAL,
                                      line_id=right_node.line_id,
                                      time=int(activity.getLowerBound()),
                                      number_of_passengers=right_node.number_of_passengers,
                                      direction=right_node.direction,
                                      line_frequency_repetition=right_node.line_frequency_repetition)

            ean.addNode(new_event)

            # add activities
            old_activity = PeriodicActivity(activityId=activity.getId(),
                                            activityType=activity.getType(),
                                            sourceEvent=left_node,
                                            targetEvent=new_event,
                                            lowerBound=activity.getLowerBound(),
                                            upperBound=activity.getUpperBound(),
                                            numberOfPassengers=0)
            max_edge_id += 1
            virtual_activity = PeriodicActivity(activityId=max_edge_id,
                                                activityType=ActivityType.VIRTUAL,
                                                sourceEvent=new_event,
                                                targetEvent=right_node,
                                                lowerBound=0,
                                                upperBound=parameters.period_length - 1,
                                                numberOfPassengers=1)

            ean.addEdge(old_activity)
            ean.addEdge(virtual_activity)

        ean.orderEdges(lambda x: x.getId())

        return ean

    @staticmethod
    def calculate_ppt1(ean: Graph[PeriodicEvent, PeriodicActivity], parameters: Parameters) -> int:
        ptt1 = 0
        for edge in ean.getEdges():
            cur_span = (edge.getRightNode().getTime() - edge.getLeftNode().getTime()) % parameters.period_length
            ptt1 += edge.getNumberOfPassengers() * cur_span

        return ptt1

    @staticmethod
    def calculate_original_timetable(extended_ean) -> Graph[PeriodicEvent, PeriodicActivity]:
        ean = DictGraph()

        for node in extended_ean.nodes:
            if node.getType() != EventType.VIRTUAL:
                ean.addNode(node)

        return ean
