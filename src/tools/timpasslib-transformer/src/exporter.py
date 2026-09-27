import logging
import shutil
import sys
from typing import List

from core.io.config import ConfigReader
from core.io.csv import CsvWriter
from core.io.od import ODReader
from core.io.periodic_ean import PeriodicEANReader, PeriodicEANWriter
from core.io.statistic import StatisticWriter
from core.model.graph import Graph
from core.model.impl.mapOD import MapOD
from core.model.od import ODPair
from core.model.periodic_ean import PeriodicActivity, PeriodicEvent
from core.util.statistic import Statistic

logger = logging.getLogger(__name__)


def write_activity(activity: PeriodicActivity) -> List[str]:
    return [str(activity.getId()), activity.getType().value, str(activity.getLeftNode().getId()),
            str(activity.getRightNode().getId()), str(activity.getLowerBound()),
            str(activity.getUpperBound())]


def write_event(event: PeriodicEvent) -> List[str]:
    return [str(event.getId()), event.getType().value, str(event.getStopId()), str(event.getLineId()),
            event.getDirection().value, str(event.getLineFrequencyRepetition())]


def eval_ean(ean_to_evaluate: Graph[PeriodicEvent, PeriodicActivity], period: int, statistic: Statistic) -> (int, int, int, int, int, int, int):
    station_id_set = set()
    line_id_set = set()
    n_events = 0
    n_activities = 0
    n_activities_fixed = 0
    n_activities_free = 0
    n_activities_restricted = 0
    events = ean_to_evaluate.getNodes()
    for event in events:
        n_events += 1
        station_id_set.add(event.getStopId())
        line_id_set.add(event.getLineId())
    activities = ean.getEdges()
    for activity in activities:
        n_activities += 1
        if activity.getLowerBound() == activity.getUpperBound():
            n_activities_fixed += 1
        elif activity.getUpperBound() - activity.getLowerBound() >= period-1:
            n_activities_free += 1
        else:
            n_activities_restricted += 1
    statistic.setValue("n_stations", len(station_id_set))
    statistic.setValue("n_lines", len(line_id_set))
    statistic.setValue("n_events", n_events)
    statistic.setValue("n_activities", n_activities)
    statistic.setValue("n_activities_fixed", n_activities_fixed)
    statistic.setValue("n_activities_free", n_activities_free)
    statistic.setValue("n_activities_restricted", n_activities_restricted)
    return len(station_id_set), len(line_id_set), n_events, n_activities, n_activities_fixed, n_activities_free, \
           n_activities_restricted


def eval_od(od_to_evaluate: MapOD, statistic: Statistic):
    od_prop_entries_greater_zero = 0
    od_prop_overall_sum = 0
    for od_pair in od_to_evaluate.getODPairs():
        od_prop_overall_sum += od_pair.getValue()
        if od_pair.getValue() > 0:
            od_prop_entries_greater_zero += 1

    statistic.setValue("od_prop_entries_greater_zero", od_prop_entries_greater_zero)
    statistic.setValue("od_prop_overall_sum",od_prop_overall_sum)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    config = ConfigReader.read(sys.argv[1])
    activity_filename = config.getStringValue("filename_timpasslib_activities")
    activity_header = config.getStringValue("header_timpasslib_activities")
    event_filename = config.getStringValue("filename_timpasslib_events")
    event_header = config.getStringValue("header_timpasslib_events")
    od_filename = config.getStringValue("filename_timpasslib_od")
    od_file_header = config.getStringValue("header_timpasslib_od")
    timetable_filename = config.getStringValue("filename_timpasslib_timetable")
    timetable_file_header = config.getStringValue("header_timpasslib_timetable")
    config_filename = config.getStringValue("filename_timpasslib_config")
    config_header = config.getStringValue("header_timpasslib_config")
    transfer_penalty = config.getStringValue("ean_change_penalty")
    ptn_name = config.getStringValue("ptn_name")
    period_length = config.getIntegerValue("period_length")
    export_timetable = config.getBooleanValue("timpasslib_export_timetable")
    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    od = ODReader.read(MapOD())
    if export_timetable:
        ean, timetable = PeriodicEANReader.read(read_timetable=True)
    else:
        ean, _ = PeriodicEANReader.read()
    logger.info("Finished reading input data")

    logger.info("Begin writing output data")
    writer = CsvWriter(config_filename, config_header)
    writer.writeLine(["ptn_name", ptn_name])
    writer.writeLine(["period_length", str(period_length)])
    writer.writeLine(["ean_change_penalty", str(transfer_penalty)])
    writer.close()

    writer = CsvWriter(activity_filename, activity_header)
    writer.writeList(ean.getEdges(), write_activity, key_function=PeriodicActivity.getId)
    writer.close()

    writer = CsvWriter(event_filename, event_header)
    writer.writeList(ean.getNodes(), write_event, key_function=PeriodicEvent.getId)
    writer.close()

    if export_timetable:
        PeriodicEANWriter.write(ean, write_events=False, write_activities=False,
              write_timetable=True, timetable=timetable, timetable_file_name=timetable_filename,
              timetable_header=timetable_file_header)

    writer = CsvWriter(od_filename, od_file_header)
    writer.writeList(od.getODPairs(), ODPair.toCsvStrings)
    writer.close()

    statistic_ean = Statistic()
    eval_ean(ean, period_length, statistic_ean)
    eval_od(od, statistic_ean)
    StatisticWriter.write(statistic_ean)

    logger.info("Finished writing output data")
