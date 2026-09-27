import logging
import subprocess
import sys
import os

from core.io.config import Config, ConfigReader
from core.io.periodic_ean import (
    PeriodicEANReader, PeriodicEANWriter,
)
from core.model.periodic_ean import ActivityType, PeriodicEvent, PeriodicActivity
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.util.mm_ean import EAN
from core.util.mm_ean_helpers import add_nonscheduled_transfers, read_and_update_event_modalities
from core.util.mm_utilities import add_modality_prefix

logger = logging.getLogger(__name__)

def create_temporary_ean_files(config: Config, joint_modality_name: str):
    """Creates temporary EAN files for timetabling with nonscheduled transfers.
    :param config: The config to read the parameters from
    :param joint_modality_name: The name of the joint modality
    :return: The names of the created event and activity files
    """
    events_filename = config.getStringValue("default_events_periodic_file").split('.')
    activities_filename = config.getStringValue("default_activities_periodic_file").split('.')
    timetable_filename = config.getStringValue("default_timetable_periodic_file").split('.')
    events_filename = events_filename[0]+"-nonscheduled."+events_filename[1]
    activities_filename = activities_filename[0]+"-nonscheduled."+activities_filename[1]
    timetable_filename = timetable_filename[0]+"-nonscheduled."+timetable_filename[1]
    with open("basis/After-Config.cnf", 'a') as file:
        file.write("include_if_exists; \"Temp-Config.cnf\"\n")
    with open("basis/Temp-Config.cnf", 'w') as file:
        file.write("default_events_periodic_file; \"" + events_filename + "\"\n")
        file.write("default_activities_periodic_file; \"" + activities_filename + "\"\n")
        file.write("default_timetable_periodic_file; \"" + timetable_filename + "\"\n")
        file.write("default_edges_file; \"" + add_modality_prefix(joint_modality_name, config.getStringValue("default_edges_file")) + "\"\n")
        file.write("default_stops_file; \"" + add_modality_prefix(joint_modality_name, config.getStringValue("default_stops_file")) + "\"\n")
        file.write("default_lines_file; \"" + add_modality_prefix(joint_modality_name, config.getStringValue("default_lines_file")) + "\"\n")
    return events_filename, activities_filename, timetable_filename

def write_modality_timetables(joint_ean: EAN, config: Config) -> None:
    """Writes the timetables for each modality to file.
    :param joint_ean: The joint EAN containing all modalities
    :param config: The config to read the parameters from
    """
    event_modality_filename = add_modality_prefix(config.getStringValue('modality_joint_name'), config.getStringValue('filename_event_modalities'))
    event_id_map, line_id_map = read_and_update_event_modalities(joint_ean, event_modality_filename)
    scheduled_modalities: list[str] = []
    all_modalities = config.getStringListValue("modalities_all")
    for mode in all_modalities:
        category = config.getStringValue(f"{mode}.modality_category")
        if category == "line-based":
            scheduled_modalities.append(mode)
        elif category == "nonscheduled":
            continue
    for modality in scheduled_modalities:
        nodes = [node for node in joint_ean.getNodes() if node.modality == modality]
        node_ids = {node.getId() for node in nodes}
        edges = [edge for edge in joint_ean.getEdges() if edge.getLeftNode().getId() in node_ids and edge.getRightNode().getId() in node_ids]
        ean = SimpleDictGraph[PeriodicEvent, PeriodicActivity]()
        for node in nodes:
            node.line_id = line_id_map[modality][node.getId()]
            node.setId(event_id_map[modality][node.getId()])
            ean.addNode(node)
        for edge in edges:
            ean.addEdge(edge)
        PeriodicEANWriter.write(
            ean=ean,
            write_events=False,
            write_activities=False,
            write_timetable=True,
            config=config,
            modality=modality,
        )

def run():
    logger.info('Started to read multimodal EAN')
    config = ConfigReader.read(sys.argv[-1])


    joint_modality_name = config.getStringValue('modality_joint_name')

    joint_ean, _ = PeriodicEANReader.read(read_timetable=False, config=config, modality=joint_modality_name)

    ns_transfer_file = config.getStringValue('filename_nonscheduled_transfers')
    if os.path.isfile(ns_transfer_file):
        logger.info('Adding nonscheduled transfers to the joint EAN')
        add_nonscheduled_transfers(joint_ean, config)

    # Create temporary EAN files for timetabling
    events_filename_orig = config.getStringValue("default_events_periodic_file")
    activities_filename_orig = config.getStringValue("default_activities_periodic_file")
    timetable_filename_orig = config.getStringValue("default_timetable_periodic_file")
    events_filename, activities_filename, timetable_filename = create_temporary_ean_files(config, joint_modality_name)

    config.put("default_events_periodic_file", events_filename)
    config.put("default_activities_periodic_file", activities_filename)
    config.put("default_timetable_periodic_file", timetable_filename)

    PeriodicEANWriter.write(ean=joint_ean, write_timetable=False, config=config)

    logger.info('Running make tim-timetable for joint modality with nonscheduled transfers')
    return_code = subprocess.run(['make', 'tim-timetable']).check_returncode()

    if return_code is None:
        ean_with_nonscheduled, ns_timetable = PeriodicEANReader.read(read_timetable=True, config=config)

        # Restore config and remove temporary files
        config.put("default_events_periodic_file", events_filename_orig)
        config.put("default_activities_periodic_file", activities_filename_orig)
        config.put("default_timetable_periodic_file", timetable_filename_orig)
        os.remove(events_filename)
        os.remove(activities_filename)
        os.remove(timetable_filename)
        os.remove("basis/Temp-Config.cnf")

        if os.path.isfile(ns_transfer_file):
            # remove nonscheduled edges
            for activity in ean_with_nonscheduled.getEdges():
                if activity.getType() == ActivityType.NONSCHEDULED:
                    ean_with_nonscheduled.removeEdge(activity)

        logger.info('Writing joint EAN and the joint timetable')
        PeriodicEANWriter.write(
            ean=ean_with_nonscheduled,
            timetable=ns_timetable,
            write_timetable=True,
            config=config,
            modality=joint_modality_name,
        )

    logger.info('Writing modality timetables')
    write_modality_timetables(ean_with_nonscheduled, config)


    logger.info('Finished creating joint timetable')


if __name__ == '__main__':
    run()
