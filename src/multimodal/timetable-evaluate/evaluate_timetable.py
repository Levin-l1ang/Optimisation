import logging
import os
import subprocess
import sys

from core.io.statistic import StatisticWriter
from core.util.statistic import Statistic

from core.io.config import ConfigReader
from core.io.od import ODReader
from core.io.periodic_ean import (
    PeriodicEANReader
)
from core.io.ptn import Link, PTNReader, Stop
from core.model.graph import Graph
from core.model.od import OD
from core.model.periodic_ean import ActivityType
from core.util.config import Config
from core.util.mm_ean_helpers import copy_ean, read_and_update_event_modalities, join_nonscheduled_mode, distribute_passengers_penalized, makeDirected, isFeasible, add_nonscheduled_transfers
from core.util.mm_ean import EAN
from core.util.mm_utilities import add_modality_prefix
from core.util.multimodal_transfer_method import buildMTGraph

logger = logging.getLogger(__name__)

def calculate_basic_statistics(ean: EAN, statistic: Statistic, period_length, joint_modality_name: str):
    """
    Calculate basic statistics for the timetable
    :param ean: EAN to calculate statistics for
    :param statistic: statistic object to store results
    :param period_length: period length of the EAN
    :param joint_modality_name: name of the joint modality
    """
    obj_ptt1 = 0
    total_slack = 0
    for activity in ean.getEdges():
        duration = activity.getDuration(period_length)
        passengers = activity.getNumberOfPassengers()
        slack = duration - activity.getLowerBound()
        obj_ptt1 += duration*passengers
        total_slack += slack
    obj_slack_average = total_slack/len(ean.getEdges())

    statistic.setValue(f"{joint_modality_name}.tim_obj_ptt1", obj_ptt1)
    statistic.setValue(f"{joint_modality_name}.tim_obj_slack_average", obj_slack_average)


def calculate_changetimes(ean: EAN, statistic: Statistic, scheduled_modalities, period_length, joint_modality_name: str):
    """
    Calculate min/max change times for the timetable
    :param ean: EAN to calculate statistics for
    :param statistic: statistic object to store results
    :param scheduled_modalities: list of scheduled modalities
    :param period_length: period length of the EAN
    :param joint_modality_name: name of the joint modality
    """
    max_intermodal_changetime = -float("inf")
    min_intermodal_changetime = float("inf")
    max_changetime_mode = {mode: -float("inf") for mode in scheduled_modalities}
    min_changetime_mode = {mode: float("inf") for mode in scheduled_modalities}
    for activity in ean.getEdges():
        passengers = activity.getNumberOfPassengers()
        leftmode = activity.getLeftNode().modality
        rightmode = activity.getRightNode().modality
        if activity.getType() == ActivityType.CHANGE and passengers > 0:
            if rightmode != leftmode:
                max_intermodal_changetime = max(max_intermodal_changetime, activity.getDuration(period_length))
                min_intermodal_changetime = min(min_intermodal_changetime, activity.getDuration(period_length))
            elif rightmode in scheduled_modalities:
                max_changetime_mode[rightmode] = max(max_changetime_mode[rightmode], activity.getDuration(period_length))
                min_changetime_mode[rightmode] = min(min_changetime_mode[rightmode], activity.getDuration(period_length))
        elif activity.getType() == ActivityType.NONSCHEDULED and passengers > 0:
            if leftmode != rightmode:
                max_intermodal_changetime = max(max_intermodal_changetime, activity.getDuration(period_length))
                min_intermodal_changetime = min(min_intermodal_changetime, activity.getDuration(period_length))
    if min_intermodal_changetime > max_intermodal_changetime: # no used change activities found
        min_intermodal_changetime = "-"
        max_intermodal_changetime = "-"
    statistic.setValue(f"{joint_modality_name}.tim_prop_intermodal_changes_od_max", max_intermodal_changetime)
    statistic.setValue(f"{joint_modality_name}.tim_prop_intermodal_changes_od_min", min_intermodal_changetime)
    for mode in scheduled_modalities:
        if min_changetime_mode[mode] > max_changetime_mode[mode]: # no used change activities found
            min_changetime_mode[mode] = "-"
            max_changetime_mode[mode] = "-"
        statistic.setValue(f"{mode}.tim_prop_changes_od_max", max_changetime_mode[mode])
        statistic.setValue(f"{mode}.tim_prop_changes_od_min", min_changetime_mode[mode])



def calculate_overcrowded_time(ean: EAN, statistic: Statistic, config: Config, total_passengers):
    """
    Calculate overcrowded time for the timetable, that is, the total time passengers spend in overcrowded vehicles
    divided by the total number of passengers
    :param ean: EAN to calculate statistics for
    :param statistic: statistic object to store results
    :param config: configuration to use
    :param total_passengers: total number of passengers in the OD matrix
    """
    period_length = config.getIntegerValue('period_length')
    scheduled_modalities: list[str] = []
    nonscheduled_modalities: list[str] = []
    all_modalities = config.getStringListValue("modalities_all")
    for mode in all_modalities:
        category = config.getStringValue(f"{mode}.modality_category")
        if category == "line-based":
            scheduled_modalities.append(mode)
        elif category == "nonscheduled":
            nonscheduled_modalities.append(mode)
    joint_modality_name = config.getStringValue('modality_joint_name')
    capacity_dict = {mode: config.getIntegerValue(f'{mode}.gen_passengers_per_vehicle') for mode in all_modalities}
    overcrowded_time = 0
    for activity in ean.getEdges():
        if (activity.getType() == ActivityType.DRIVE) or (activity.getType() == ActivityType.WAIT):
            passengers = activity.getNumberOfPassengers()
            mode = activity.getRightNode().modality
            if passengers > capacity_dict[mode]:
                overcrowded_time += passengers*activity.getDuration(period_length)
    overcrowded_time_average = overcrowded_time/total_passengers
    statistic.setValue(f"{joint_modality_name}.tim_overcrowded_time_average", overcrowded_time_average)


def calculate_slacks(ean: EAN, statistic: Statistic, period_length, joint_modality_name: str):
    """
    Calculate average slack per activity type for the timetable
    :param ean: EAN to calculate statistics for
    :param statistic: statistic object to store results
    :param period_length: period length of the EAN
    :param joint_modality_name: name of the joint modality
    """
    total_weighted_slack_type = {type: 0 for type in (ActivityType)}
    total_passengers_type = {type: 0 for type in (ActivityType)}
    for activity in ean.getEdges():
        duration = activity.getDuration(period_length)
        passengers = activity.getNumberOfPassengers()
        slack = duration - activity.getLowerBound()
        total_weighted_slack_type[activity.getType()] += slack*passengers
        total_passengers_type[activity.getType()] += passengers
    for type in (ActivityType):
        if total_passengers_type[type] > 0:
            statistic.setValue(f"{joint_modality_name}.tim_obj_slack_{type.value[1:-1]}_average", total_weighted_slack_type[type]/total_passengers_type[type])

def calculate_changes(ean: EAN, statistic: Statistic, scheduled_modalities, joint_modality_name: str):
    """
    Calculate number of changes per activity type for the timetable
    :param ean: EAN to calculate statistics for
    :param statistic: statistic object to store results
    :param scheduled_modalities: list of scheduled modalities
    :param joint_modality_name: name of the joint modality
    """
    mode_changes_amount: dict[tuple[str, str], int] = {}
    for mode1 in scheduled_modalities:
        for mode2 in scheduled_modalities:
            mode_changes_amount[(mode1, mode2)] = 0

    for activity in ean.getEdges():
        passengers = activity.getNumberOfPassengers()
        leftmode = activity.getLeftNode().modality
        rightmode = activity.getRightNode().modality
        if activity.getType() == ActivityType.CHANGE:
            mode_changes_amount[(leftmode, rightmode)] += passengers
    for mode in scheduled_modalities:
        mode_changes = mode_changes_amount[(mode, mode)]
        statistic.setValue(f"{mode}.tim_number_of_transfers", str(mode_changes))
    total_changes = sum([changes for modes, changes in mode_changes_amount.items()])
    statistic.setValue(f"{joint_modality_name}.tim_number_of_transfers", str(total_changes))
    intermodal_changes = sum([changes for modes, changes in mode_changes_amount.items() if modes[0] != modes[1]])
    statistic.setValue(f"{joint_modality_name}.tim_number_of_intermodal_transfers", str(intermodal_changes))


def calculate_travel_times(complete_ean: EAN, od: OD, config: Config, modalities, penalty: bool):
    """
    Calculate average travel time per mode and overall
    :param complete_ean: EAN to calculate statistics for
    :param od: OD matrix to use
    :param config: configuration to use
    :param modalities: list of modalities to calculate statistics for
    :param penalty: whether to use penalties or not (perceived travel time vs actual travel time)
    :return: average travel time, average travel time per mode
    """
    period_length = config.getIntegerValue('period_length')
    total_passengers = od.computeNumberOfPassengers()
    intermodal_penalty = config.getDoubleValue('ean_intermodal_change_penalty')
    change_penalty = config.getDoubleValue('ean_change_penalty')
    nonscheduled_penalty = config.getDoubleValue('ean_nonscheduled_penalty')

    logger.info('Distributing passengers')
    if penalty:
        distribute_passengers_penalized(complete_ean, od, config, useTimetable=True)
    else:
        config.put('ean_intermodal_change_penalty', 0)
        config.put('ean_change_penalty', 0)
        config.put('ean_nonscheduled_penalty', 0)
        distribute_passengers_penalized(complete_ean, od, config, useTimetable=True)
        config.put('ean_intermodal_change_penalty', intermodal_penalty)
        config.put('ean_change_penalty', change_penalty)
        config.put('ean_nonscheduled_penalty', nonscheduled_penalty)

    total_pt = 0
    mode_pt = {mode: 0 for mode in modalities}

    for activity in complete_ean.getEdges():
        passengers = activity.getNumberOfPassengers()
        leftmode = activity.getLeftNode().modality
        rightmode = activity.getRightNode().modality
        duration = activity.getDuration(period_length)
        total_pt += duration * passengers
        if activity.getType() != ActivityType.NONSCHEDULED:
            # don't count intermodal transfers to any modality
            if not ((activity.getType() == ActivityType.CHANGE) and (leftmode != rightmode)):
                mode_pt[leftmode] += duration * passengers
            # add transfer penalties
            if penalty and activity.getType() == ActivityType.CHANGE:
                if leftmode != rightmode:
                    total_pt += intermodal_penalty * passengers
                else:
                    total_pt += change_penalty * passengers
                    mode_pt[leftmode] += change_penalty * passengers
        else:
            mode_pt["nonscheduled"] += duration * passengers
            if penalty:
                total_pt += nonscheduled_penalty * passengers
                mode_pt["nonscheduled"] += nonscheduled_penalty * passengers

    return total_pt/total_passengers, {mode: mode_pt[mode]/total_passengers for mode in modalities}



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
    period_length = config.getIntegerValue('period_length')
    transfer_penalty = config.getDoubleValue('ean_intermodal_change_penalty')
    penalty_algorithm = config.getStringValue('multimodal_transfer_penalty_algorithm')



    logger.info('Reading joint EAN and nonscheduled PTNs')
    nonscheduled_ptns: dict[str, Graph[Stop, Link]] = {}
    for mode in nonscheduled_modalities:
        undir_ptn = PTNReader.read(config=config, modality=mode)
        nonscheduled_ptns[mode] = makeDirected(undir_ptn)
    scheduled_ean, _ = PeriodicEANReader.read(read_timetable=True, config=config, modality=joint_modality_name)
    event_modality_filename = add_modality_prefix(config.getStringValue('modality_joint_name'), config.getStringValue('filename_event_modalities'))
    read_and_update_event_modalities(scheduled_ean, event_modality_filename)

    od = ODReader.readInfrastructureOd(None, config=config)

    total_passengers = od.computeNumberOfPassengers()

    # Save a copy of scheduled EAN before adding nonscheduled transfers
    complete_ean = copy_ean(scheduled_ean)

    # Add nonscheduled transfers to the scheduled EAN to get correct passenger numbers on nonscheduled activities
    if len(nonscheduled_modalities) > 0:
        logger.info('Adding nonscheduled transfers to the scheduled EAN')
        add_nonscheduled_transfers(scheduled_ean, config)

    statistic = Statistic()

    statistic.setValue(f"{joint_modality_name}.tim_feasible", str(isFeasible(scheduled_ean, period_length)))

    calculate_basic_statistics(scheduled_ean, statistic, period_length, joint_modality_name)



    if config.getBooleanValue("ean_eval_extended"):

        calculate_overcrowded_time(scheduled_ean, statistic, config, total_passengers)

        calculate_changetimes(scheduled_ean, statistic, scheduled_modalities, period_length, joint_modality_name)

        calculate_slacks(scheduled_ean, statistic, period_length, joint_modality_name)

        calculate_changes(scheduled_ean, statistic, scheduled_modalities, joint_modality_name)


    # Join nonscheduled PTNs to EAN.
    # The idea is that we'll add nonscheduled modes to complete_ean
    # after which we'll redistribute passengers using the timetable.
    # We'll then redistribute passengers to the corresponding edges in
    # scheduled_ean, and write it back. Then we can create a timetable.

    logger.info('Creating joint scheduled-non-scheduled EAN')

    modalities = []
    for mode in scheduled_modalities:
        modalities.append(mode)
    modalities.append("nonscheduled")

    nonscheduled_ptn = buildMTGraph(graphs=nonscheduled_ptns.values(), penalty_algorithm=penalty_algorithm, transfer_penalty_rate=transfer_penalty)
    _, _ = join_nonscheduled_mode(complete_ean, nonscheduled_ptn, od, config)

    total_time, mode_time = calculate_travel_times(complete_ean, od, config, modalities, penalty=False)
    total_penalized_time, mode_penalized_time = calculate_travel_times(complete_ean, od, config, modalities, penalty=True)
    statistic.setValue(f"{joint_modality_name}.tim_time_average", str(total_time))
    statistic.setValue(f"{joint_modality_name}.tim_perceived_time_average", str(total_penalized_time))
    for mode in modalities:
        statistic.setValue(f"{mode}.tim_time_average", str(mode_time[mode]))
        statistic.setValue(f"{mode}.tim_perceived_time_average", str(mode_penalized_time[mode]))


    logger.info('Writing statistic')

    StatisticWriter.write(statistic=statistic, config=config)



if __name__ == "__main__":
    run()