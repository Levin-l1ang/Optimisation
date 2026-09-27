import logging
import shutil
import sys

from core.exceptions.exceptions import LinTimException
from core.exceptions.input_exceptions import InputFileException
from core.io.config import ConfigReader
from core.io.csv import CsvWriter
from core.io.lines import LineWriter
from core.io.od import ODReader, ODWriter
from core.io.periodic_ean import PeriodicEANWriter
from core.io.ptn import PTNWriter
from core.model.activityType import ActivityType
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.model.lines import LineDirection, LinePool, Line
from core.model.ptn import Stop, Link
from timpassreader import PeriodicEANReaderTimPassLib


def adapt_config_file(timpasslib_conf_filename, conf_filename):
    parameters_to_update = {}
    logger = logging.getLogger(__name__)
    logger.debug("Reading file {}".format(timpasslib_conf_filename))

    with open(timpasslib_conf_filename, newline='', encoding="utf8") as input_file_timpasslib:
        for line_number, line in enumerate(input_file_timpasslib):
            line = line.split('#')[0].strip()
            if not line:
                continue
            else:
                tokens = line.split(";")
                tokens = [token.strip() for token in tokens]
                parameters_to_update[tokens[0]] = tokens[1]

    lines_to_write = []

    with open(conf_filename, newline='', encoding="utf8") as input_file_config:
        for line_number, line in enumerate(input_file_config):
            line_orig = line
            line = line.split('#')[0].strip()
            line_changed = False
            if line:
                tokens = line.split(";")
                tokens = [token.strip() for token in tokens]
                for param, value in parameters_to_update.items():
                    if tokens[0] == param:
                        lines_to_write.append(f"{param}; {value}")
                        line_changed = True
            if not line_changed:
                lines_to_write.append(line_orig.strip())

    logger.info("Begin writing output data")
    writer = CsvWriter(conf_filename)
    for line in lines_to_write:
        writer.writeLine([line])
    writer.close()

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    config = ConfigReader.read(sys.argv[1])
    timpasslib_activity_filename = config.getStringValue("filename_timpasslib_activities")
    timpasslib_activity_header = config.getStringValue("header_timpasslib_activities")
    timpasslib_event_filename = config.getStringValue("filename_timpasslib_events")
    timpasslib_event_header = config.getStringValue("header_timpasslib_events")
    timpasslib_od_filename = config.getStringValue("filename_timpasslib_od")
    timpasslib_od_file_header = config.getStringValue("header_timpasslib_od")
    timpasslib_timetable_filename = config.getStringValue("filename_timpasslib_timetable")
    timpasslib_timetable_file_header = config.getStringValue("header_timpasslib_timetable")
    timpasslib_config_filename = config.getStringValue("filename_timpasslib_config")
    activity_filename = config.getStringValue("default_activities_periodic_file")
    activity_header = config.getStringValue("activities_header_periodic")
    event_filename = config.getStringValue("default_events_periodic_file")
    event_header = config.getStringValue("events_header_periodic")
    od_filename = config.getStringValue("default_od_file")
    timetable_filename = config.getStringValue("default_timetable_periodic_file")
    import_timetable = config.getBooleanValue("timpasslib_import_timetable")
    config_filename = sys.argv[1]
    logger.info("Finished reading configuration")

    logger.info("Begin adapting config file")
    adapt_config_file(timpasslib_config_filename, config_filename)
    logger.info("Finished adapting config file")

    logger.info("Begin reading EAN and timetable")
    ean, timetable = PeriodicEANReaderTimPassLib.readTimPassLib(read_events=True, read_activities=True, read_timetable=import_timetable)
    logger.info("Finished reading EAN and timetable")

    logger.info("Begin constructing PTN")
    ptn = SimpleDictGraph()
    stop_id_list=[]
    directed = True

    for event in ean.getNodes():
        if event.getStopId() not in stop_id_list:
            stop_id_list.append(event.getStopId())
        if event.getDirection() == LineDirection.BACKWARDS:
            directed = False
    for stop_id in stop_id_list:
        ptn.addNode(Stop(stop_id, str(stop_id), str(stop_id), 0, 0, 0, 0))
    max_edge_id = 1
    for activity in ean.getEdges():
        if activity.getType()==ActivityType.DRIVE:
            left_stop = ptn.getNode(activity.getLeftNode().getStopId())
            right_stop = ptn.getNode(activity.getRightNode().getStopId())
            if ptn.get_edge_by_nodes(left_stop, right_stop) or ((not directed) and ptn.get_edge_by_nodes(right_stop,left_stop)):
                continue
            potentialLink = Link(max_edge_id,left_stop,right_stop,0, activity.getLowerBound(), activity.getUpperBound(),directed)
            ptn.addEdge(potentialLink)
            max_edge_id += 1

    logger.info("Finished creating PTN")

    logger.info("Begin creating line pool")

    line_pool = LinePool()
    line_id_list = []
    line_frequency_dict = {}
    for event in ean.getNodes():
        current_line_id = event.getLineId()
        # line frequency is set to the highest line repetition number for the respective line id
        if current_line_id in line_frequency_dict:
            line_frequency_dict[current_line_id] = max(event.getLineFrequencyRepetition(), line_frequency_dict[current_line_id])
        else:
            line_frequency_dict[current_line_id] = event.getLineFrequencyRepetition()
        # for each line id, only one line should be created
        if current_line_id in line_id_list or event.getDirection() == LineDirection.BACKWARDS or event.getLineFrequencyRepetition()>1:
            continue
        line_id_list.append(current_line_id)
        current_line = Line(current_line_id, directed)
        current_event=event
        # check forward: find line path in ean going out of event and add the corresponding edges to the line
        logger.debug(f"Start searching forward activities for event {event}.")
        has_outgoing_edge=True
        while has_outgoing_edge:
            has_outgoing_edge = False
            for outgoing_activity in ean.getOutgoingEdges(current_event):
                next_event = outgoing_activity.getRightNode()
                # make sure to only consider events for the correct line direction/frequency repetition
                if next_event.getLineId() == current_line_id and next_event.getLineFrequencyRepetition() == 1 and next_event.getDirection() == LineDirection.FORWARDS:
                    has_outgoing_edge = True
                    # only drive activities correspond to line edges
                    if outgoing_activity.getType() == ActivityType.DRIVE:
                        left_stop = ptn.getNode(current_event.getStopId())
                        right_stop = ptn.getNode(next_event.getStopId())
                        if ptn.get_edge_by_nodes(left_stop, right_stop):
                            current_line.addLink(ptn.get_edge_by_nodes(left_stop, right_stop))
                        elif (not directed) and ptn.get_edge_by_nodes(right_stop, left_stop):
                            current_line.addLink(ptn.get_edge_by_nodes(right_stop, left_stop))
                        else:
                            raise LinTimException(f"No edge from {left_stop.getId()} to {right_stop.getId()} in PTN to be added to line {current_line_id} (from activity {outgoing_activity}).")
                    current_event = next_event
        # check backward: find line path in ean going into event and add the corresponding edges to the line
        logger.debug(f"Start searching backward activities for event {event}.")
        current_event = event
        has_incoming_edge = True
        while has_incoming_edge:
            has_incoming_edge=False
            for incoming_activity in ean.getIncomingEdges(current_event):
                next_event = incoming_activity.getLeftNode()
                # make sure to only consider events for the correct line direction/frequency repetition
                if next_event.getLineId() == current_line_id and next_event.getLineFrequencyRepetition() == 1 and next_event.getDirection() == LineDirection.FORWARDS:
                    has_incoming_edge = True
                    # only drive activities correspond to line edges
                    if incoming_activity.getType() == ActivityType.DRIVE:
                        right_stop= ptn.getNode(current_event.getStopId())
                        left_stop = ptn.getNode(next_event.getStopId())
                        if ptn.get_edge_by_nodes(left_stop, right_stop):
                            current_line.addLink(ptn.get_edge_by_nodes(left_stop, right_stop))
                        elif (not directed) and ptn.get_edge_by_nodes(right_stop, left_stop):
                            current_line.addLink(ptn.get_edge_by_nodes(right_stop, left_stop))
                        else:
                            raise LinTimException(
                                f"No edge from {left_stop.getId()} to {right_stop.getId()} in PTN to be added to line {current_line_id} (from activity {incoming_activity}).")
                    current_event = next_event

        line_pool.addLine(current_line)

    logger.info("Finished creating line pool")

    logger.info("Begin writing line concept")
    for line in line_pool.getLines():
        line.setFrequency(line_frequency_dict[line.getId()])
    logger.info("Finished writing line concept")

    od = ODReader.read(None, file_name=timpasslib_od_filename)

    logger.info("Begin writing output")
    PTNWriter.write(ptn)
    ODWriter.write(ptn,od)
    LineWriter.write(line_pool)
    PeriodicEANWriter.write(ean, write_timetable=import_timetable)
    logger.info("Finished writing output")
