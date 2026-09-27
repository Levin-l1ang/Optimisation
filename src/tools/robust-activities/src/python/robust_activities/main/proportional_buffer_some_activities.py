import logging
import sys

from core.io.config import ConfigReader
from core.io.periodic_ean import PeriodicEANReader
from core.io.ptn import PTNReader
from core.util.config import Config
from robust_activities.io.buffered_activity import write_buffered_activities

from robust_activities.util.buffers import apply_buffers, parse_buffered_links_and_stops, transform_to_buffered_ean

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    if len(sys.argv) != 2:
        logger.fatal("Program takes exactly one argument, the name of the config to read")
        exit(1)
    ConfigReader.read(sys.argv[1])
    stop_file_name = Config.getStringValueStatic("default_stops_file")
    link_file_name = Config.getStringValueStatic("default_edges_file")
    directed_ptn = not Config.getBooleanValueStatic("ptn_is_undirected")
    event_file_name = Config.getStringValueStatic("default_events_periodic_file")
    # We want to get the unbuffered activity file, therefore we set use_buffered_activities to false, just to be sure
    Config.putStatic("use_buffered_activities", "false")
    activity_file_name = Config.getStringValueStatic("default_activities_periodic_file")
    buffered_activity_file_name = Config.getStringValueStatic("default_activity_buffer_file")
    buffered_weight_activity_file_name = Config.getStringValueStatic("default_activity_buffer_weight_file")
    buffered_link_string = Config.getStringValueStatic("rob_buffer_link_list")
    wait_buffer_amount = Config.getStringValueStatic("rob_buffer_on_wait_activity")
    drive_buffer_amount = Config.getIntegerValueStatic("rob_buffer_on_drive_activity")
    drive_buffer_ratio = Config.getDoubleValueStatic("rob_proportional_drive_activity_buffer")
    buffer_stop_percentage = Config.getDoubleValueStatic("rob_buffer_stop_percentage")
    buffer_link_percentage = Config.getDoubleValueStatic("rob_buffer_link_percentage")
    logging.info("Begin reading input files")
    ptn = PTNReader.read(stop_file_name, link_file_name, directed_ptn)
    ean = PeriodicEANReader.read(event_file_name, activity_file_name)[0]
    logging.info("Finished reading input files")
    logging.info("Begin applying some proportional buffers to the ean")
    buffered_ean = transform_to_buffered_ean(ean)
    buffered_links, buffered_stops = parse_buffered_links_and_stops(buffered_link_string, buffer_stop_percentage,
                                                                    buffer_link_percentage, ptn, ean)
    number_of_buffered_activities = apply_buffers(buffered_ean, ptn, buffered_links, buffered_stops, wait_buffer_amount,
                                                  drive_buffer_amount, drive_buffer_ratio)
    logging.info("Finished applying some proportional buffers to the ean, added {} buffers"
                 .format(number_of_buffered_activities))
    logging.info("Begin writing output data")
    write_buffered_activities(buffered_ean, buffered_activity_file_name, buffered_weight_activity_file_name)
    logging.info("Finished writing output data")
