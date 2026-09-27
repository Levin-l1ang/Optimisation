import logging
import sys

from core.io.statistic import StatisticWriter
from core.util.statistic import Statistic

from core.io.config import ConfigReader
from core.io.lines import LineReader
from core.io.od import ODReader
from core.io.periodic_ean import (
    PeriodicEANReader
)
from core.io.ptn import Link, PTNReader, Stop
from core.model.graph import Graph
from core.model.lines import Line, LinePool
from core.model.periodic_ean import ActivityType, EventType
from core.util.config import Config
from core.util.mm_ean_helpers import getWeight, read_and_update_event_modalities, join_nonscheduled_mode, distribute_passengers_penalized, makeDirected
from core.util.mm_ean import EAN
from core.util.multimodal_transfer_method import buildMTGraph
from core.util.mm_utilities import add_modality_prefix


logger = logging.getLogger(__name__)

def count_events_and_activities(ean: EAN, statistic: Statistic, joint_modality_name: str, modality=""):
    """
    Count events and activities in the EAN, either for all modalities or for a specific modality.
    Update the statistic object with the counts.
    :param ean: EAN.
    :param statistic: The Statistic object to update with counts.
    :param joint_modality_name: The name used for the joint modality in statistics.
    :param modality: The specific modality to filter by (empty string for all modalities).
    """
    if modality == "":
        modality_name = joint_modality_name
    else:
        modality_name = modality
    events_per_type = {type: 0 for type in (EventType)}
    for event in ean.getNodes():
        if modality == "" or event.modality == modality:
            events_per_type[event.getType()] += 1

    statistic.setValue(f"{modality_name}.ean_prop_events", str(sum(events_per_type.values())))
    for type in (EventType):
        if events_per_type[type] > 0:
            statistic.setValue(f"{modality_name}.ean_prop_events_{type.value[1:-1]}", str(events_per_type[type]))

    activities_per_type = {type: 0 for type in (ActivityType)}
    od_activities_per_type = {type: 0 for type in (ActivityType)}
    for activity in ean.getEdges():
        if modality == "" or ((activity.getLeftNode().modality == modality) and (activity.getRightNode().modality == modality)):
            activities_per_type[activity.getType()] += 1
            if activity.getNumberOfPassengers() > 0:
                od_activities_per_type[activity.getType()] += 1

    statistic.setValue(f"{modality_name}.ean_prop_activities", str(sum(activities_per_type.values())))
    statistic.setValue(f"{modality_name}.ean_prop_activities_od", str(sum(od_activities_per_type.values())))
    for type in (ActivityType):
        if activities_per_type[type] > 0:
            statistic.setValue(f"{modality_name}.ean_prop_activities_{type.value[1:-1]}", str(activities_per_type[type]))
        if od_activities_per_type[type] > 0:
            statistic.setValue(f"{modality_name}.ean_prop_activities_od_{type.value[1:-1]}", str(od_activities_per_type[type]))

def evaluate_max_load(ean: EAN, scheduled_ptns: dict[str, Graph[Stop, Link]], line_concept_mode: dict[str, list[Line]], capacity_dict: dict[str, int], config: Config):
    """
    Evaluate the maximum load factor on scheduled public transport links based on the passenger distribution in the EAN.
    :param ean: EAN.
    :param scheduled_ptns: A dictionary mapping modality names to their corresponding scheduled PTNs (graphs).
    :param line_concept_mode: A dictionary mapping modality names to their corresponding line concepts (lists of lines).
    :param capacity_dict: A dictionary mapping modality names to their vehicle capacities
    :param config: Configuration object.
    :return: The maximum load factor found across all scheduled PTN links.
    """
    forward_load = {}
    backward_load = {}
    invalid_loads = {}
    for ptn in scheduled_ptns.values():
        for edge in ptn.getEdges():
            forward_load[edge] = 0
            backward_load[edge] = 0
    for activity in ean.getEdges():
        if (activity.getNumberOfPassengers() == 0) or (activity.getType() != ActivityType.DRIVE):
            continue
        mode = activity.getLeftNode().modality

        edge = scheduled_ptns[mode].get_edge_by_function(lambda edge: (edge.getLeftNode().getId() == activity.getLeftNode().getStopId()) and (edge.getRightNode().getId() == activity.getRightNode().getStopId()), True)
        if edge == None:
            if scheduled_ptns[mode].isDirected():
                logger.error(f"Could not find ptn link to activity {activity.getId()}")
            else:
                edge = scheduled_ptns[mode].get_edge_by_function(lambda edge: (edge.getRightNode().getId() == activity.getLeftNode().getStopId()) and (edge.getLeftNode().getId() == activity.getRightNode().getStopId()), True)
                if edge == None:
                    logger.error(f"Could not find ptn link to activity {activity.getId()}")
        elif edge.getLeftNode().getId() == activity.getLeftNode().getStopId(): # forward direction
            forward_load[edge] += activity.getNumberOfPassengers()
        else: # backward direction
            backward_load[edge] += activity.getNumberOfPassengers()
    max_load_factor = -1
    for mode, ptn in scheduled_ptns.items():
        for edge in ptn.getEdges():
            capacity = 0
            for line in line_concept_mode[mode]:
                if edge in line.getLinePath().getEdges():
                    capacity += line.getFrequency()*capacity_dict[mode]
            load = max(forward_load[edge], backward_load[edge])
            if load == 0:
                continue
            elif capacity == 0:
                max_load_factor = float("inf")
                worst_edge = edge
            else:
                load_factor = load/capacity
                if load_factor > max_load_factor:
                    max_load_factor = load_factor
                    worst_edge = edge
                if load_factor > 1:
                    logger.info(f"Found invalid ptn edge {edge.getId()} on modality {mode} with load of {load} and a capacity of {capacity}")
                    invalid_loads[edge] = [mode, load, capacity]
    logger.info(f"Maximal load factor found: {max_load_factor} for link {worst_edge.getId()} on modality {mode}")
    if max_load_factor > 1:
        with open(config.getStringValue("filename_invalid_loads"), "w") as f:
            f.write("# modality; link-id; load; capacity")
            for edge, value in invalid_loads.items():
                f.write(f"{value[1]}; {edge.getId()}; {value[2]}; {value[3]}\n")

    return max_load_factor

def ean_extended_eval(ean: EAN, statistic: Statistic, config: Config, joint_modality_name: str, joint_ptn: Graph[Stop, Link] = None, linepool_joint: LinePool = None, modality=""):
    """
    Perform extended evaluation metrics on the EAN, either for all modalities or for a specific modality.
    Update the statistic object with the results.
    :param ean: EAN.
    :param statistic: The Statistic object to update with results.
    :param config: Configuration object.
    :param joint_modality_name: The name used for the joint modality in statistics.
    :param modality: The specific modality to filter by (empty string for all modalities).
    """
    if modality == "":
        modality_name = joint_modality_name
    else:
        modality_name = modality

    if joint_ptn is None:
        joint_ptn = PTNReader.read(config=config, modality=joint_modality_name)
    if linepool_joint is None:
        linepool_joint = LineReader.read(ptn=joint_ptn, read_frequencies=True, modality=joint_modality_name)
    period_length = config.getIntegerValue('period_length')
    prop_activities_feas = 0
    prop_activites_obj = 0
    prop_changes_od_max = -float("inf")
    prop_changes_od_min = float("inf")
    headways_between_departures_only = True
    interstation_headways_exist = False
    for activity in ean.getEdges():
        if modality == "" or ((activity.getLeftNode().modality == modality) and (activity.getRightNode().modality == modality)):
            if activity.getUpperBound() - activity.getLowerBound() < period_length - 1:
                prop_activities_feas += 1
                prop_activites_obj += 1
            elif activity.getNumberOfPassengers() > 0:
                prop_activites_obj += 1
            if activity.getType() == ActivityType.CHANGE and activity.getNumberOfPassengers() > 0:
                weight = getWeight(activity, config, useTimetable=False, joint_ptn=joint_ptn, linepool_joint=linepool_joint)
                if weight < prop_changes_od_min:
                    prop_changes_od_min = weight
                if weight > prop_changes_od_max:
                    prop_changes_od_max = weight
            if activity.getType() == ActivityType.HEADWAY:
                if (activity.getLeftNode().getType() != EventType.DEPARTURE) or (activity.getRightNode().getType() != EventType.DEPARTURE):
                    headways_between_departures_only = False
                if activity.getLeftNode().getStopId() != activity.getRightNode().getStopId():
                    interstation_headways_exist = True
    if prop_changes_od_min > prop_changes_od_max: # no used change activities found
        prop_changes_od_min = "-"
        prop_changes_od_max = "-"
    statistic.setValue(f"{modality_name}.ean_prop_activities_feas", str(prop_activities_feas))
    statistic.setValue(f"{modality_name}.ean_prop_activities_objective", str(prop_activites_obj))
    statistic.setValue(f"{modality_name}.ean_prop_changes_od_max", str(prop_changes_od_max))
    statistic.setValue(f"{modality_name}.ean_prop_changes_od_min", str(prop_changes_od_min))
    statistic.setValue(f"{modality_name}.ean_prop_headways_dep", str(headways_between_departures_only))
    statistic.setValue(f"{modality_name}.ean_prop_headways_interstation", str(interstation_headways_exist))


"""
Evaluation metrics:
- Mode-specific travel time
- Total travel time
- Penalized travel time
- Number of transfers between modes
- Number of transfers within specific modes
- Penalized travel time within specific modes """

def run():
    logger.info('Started to create multimodal EAN')
    logger.info('Rewriting filenames to use "joint" modality for filenames')
    config = ConfigReader.read(sys.argv[-1])
    joint_modality_name = config.getStringValue('modality_joint_name')

    scheduled_modalities: list[str] = []
    nonscheduled_modalities: list[str] = []
    all_modalities = config.getStringListValue("modalities_all")
    for mode in all_modalities:
        category = config.getStringValue(f"{mode}.modality_category")
        if category == "line-based":
            scheduled_modalities.append(mode)
        elif category == "nonscheduled":
            nonscheduled_modalities.append(mode)
    event_modality_filename = config.getStringValue('filename_event_modalities')
    transfer_penalty = config.getDoubleValue('ean_intermodal_change_penalty')
    penalty_algorithm = config.getStringValue('multimodal_transfer_penalty_algorithm')



    logger.info('Reading joint EAN and nonscheduled PTNs')
    nonscheduled_ptns: dict[str, Graph[Stop, Link]] = {}
    for mode in nonscheduled_modalities:
        undir_ptn = PTNReader.read(config=config, modality=mode)
        nonscheduled_ptns[mode] = makeDirected(undir_ptn)
    ean, _ = PeriodicEANReader.read(read_timetable=False, config=config, modality=joint_modality_name)
    event_modality_filename = add_modality_prefix(joint_modality_name, event_modality_filename)
    read_and_update_event_modalities(ean, event_modality_filename)

    od = ODReader.readInfrastructureOd(None, config=config)

    logger.info('Creating joint scheduled-non-scheduled EAN')

    nonscheduled_ptn = buildMTGraph(graphs=nonscheduled_ptns.values(), penalty_algorithm=penalty_algorithm, transfer_penalty_rate=transfer_penalty)
    _, _ = join_nonscheduled_mode(ean, nonscheduled_ptn, od, config)

    logger.info('Distributing passengers')

    distribute_passengers_penalized(ean, od, config, useTimetable=False)


    statistic = Statistic()

    count_events_and_activities(ean, statistic, joint_modality_name)

    for mode in all_modalities:
        count_events_and_activities(ean, statistic, joint_modality_name, modality=mode)

    # Travel time evaluation
    joint_ptn = PTNReader.read(config=config, modality=joint_modality_name)
    linepool_joint = LineReader.read(ptn=joint_ptn, read_frequencies=True, modality=joint_modality_name)

    weighted_time_total = 0
    passengers_total = 0
    weighted_time_mode = {mode: 0 for mode in scheduled_modalities}
    passengers_mode = {mode: 0 for mode in scheduled_modalities}
    weighted_time_mode["nonscheduled"] = 0
    passengers_mode["nonscheduled"] = 0
    for activity in ean.getEdges():
        weighted_time_total += activity.getNumberOfPassengers()*getWeight(activity, config, useTimetable=False, joint_ptn=joint_ptn, linepool_joint=linepool_joint)
        passengers_total += activity.getNumberOfPassengers()
        if activity.getRightNode().modality == activity.getLeftNode().modality:
            if activity.getType() == ActivityType.NONSCHEDULED:
                weighted_time_mode["nonscheduled"] += activity.getNumberOfPassengers()*getWeight(activity, config, useTimetable=False, joint_ptn=joint_ptn, linepool_joint=linepool_joint)
                passengers_mode["nonscheduled"] += activity.getNumberOfPassengers()
            else:
                mode = activity.getLeftNode().modality
                weighted_time_mode[mode] += activity.getNumberOfPassengers()*getWeight(activity, config, useTimetable=False, joint_ptn=joint_ptn, linepool_joint=linepool_joint)
                passengers_mode[mode] += activity.getNumberOfPassengers()

    time_average = weighted_time_total/passengers_total
    statistic.setValue(f"{joint_modality_name}.ean_time_average", str(time_average))
    for mode in weighted_time_mode:
        if passengers_mode[mode] > 0:
           statistic.setValue(f"{mode}.ean_time_average", str(weighted_time_mode[mode]/passengers_mode[mode]))

    # Extended evaluation
    if config.getBooleanValue("ean_eval_extended"):
        ean_extended_eval(ean, statistic, config, joint_modality_name, joint_ptn)
        for mode in all_modalities:
            ean_extended_eval(ean, statistic, config, joint_modality_name, joint_ptn, modality=mode)
        scheduled_ptns = {}
        line_concept_mode = {}
        for mode in scheduled_modalities:
            scheduled_ptns[mode] = PTNReader.read(config=config, modality=mode)
            line_concept_mode[mode] = LineReader.read(ptn=scheduled_ptns[mode], read_frequencies=True, modality=mode).getLineConcept()
        capacity_dict = {mode: config.getIntegerValue(f'{mode}.gen_passengers_per_vehicle') for mode in all_modalities}
        max_load_factor = evaluate_max_load(ean, scheduled_ptns, line_concept_mode, capacity_dict, config)
        statistic.setValue(f"{joint_modality_name}.ean_max_load_factor", str(max_load_factor))

    logger.info('Writing statistic')

    StatisticWriter.write(statistic=statistic, config=config)



if __name__ == "__main__":
    run()