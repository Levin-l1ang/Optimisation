import logging
import sys
import os.path

from core.io.config import Config, ConfigReader
from core.io.csv import CsvWriter
from core.io.lines import LinePool, LineReader, LineWriter
from core.io.od import MapOD, ODReader, ODWriter
from core.io.periodic_ean import (
    PeriodicEANReader, PeriodicEANWriter,
)
from core.io.ptn import Link, PTNReader, PTNWriter, Stop
from core.model.graph import Graph
from core.model.periodic_ean import (
    PeriodicEvent, PeriodicActivity, PeriodicTimetable,
)
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.util.mm_utilities import add_modality_prefix
from core.util.mm_ean import EAN, EANJoiner
from core.util.multimodal_transfer_method import buildMTGraph
from core.util.mm_ean_helpers import distribute_passengers_penalized, makeDirected, join_nonscheduled_mode, copy_ean, write_nonscheduled_transfers, read_and_update_event_modalities

logger = logging.getLogger(__name__)

class Counter:
    # Simple counter class to generate unique ids
    def __init__(self, first_value: int = 0):
        self.value = first_value

    def __call__(self) -> int:
        self.value += 1
        return self.value

def fixed_event_output(event: PeriodicEvent) -> list[str]:
    """
    Outputs event in fixed timetable format: id;time;time
    :param event: event to output
    :return: list of strings representing the event
    """
    return [str(event.getId()), str(event.getTime()), str(event.getTime())]


def write_fixed_timetable(
        nodes: list[PeriodicEvent],
        fixed_timetable_file: str = '',
        fixed_timetable_header: str = '',
        config: Config = Config.getDefaultConfig(),
) -> None:
    """
    Write fixed timetable for given nodes to file.
    :param nodes: nodes to write
    :param fixed_timetable_file: file to write to
    :param fixed_timetable_header: header for the file
    :param config: configuration to use if file or header not given
    """
    if not fixed_timetable_file:
        fixed_timetable_file = config.getStringValue('filename_tim_fixed_times')
    if not fixed_timetable_header:
        fixed_timetable_header = config.getStringValue('timetable_header_periodic_fixed')
    CsvWriter.writeListStatic(
        add_modality_prefix(config.getStringValue("modality_joint_name"), fixed_timetable_file),
        nodes,
        lambda e: fixed_event_output(e),
        PeriodicEvent.getId,
        fixed_timetable_header,
    )


def _remap_edge_ids(
    ptns: dict[str, Graph[Stop, Link]],
    config: Config,
) -> Graph[Stop, Link]:
    """
    Remap edge ids to be unique between all modalities
    :param ptns: dictionary of PTNs for each modality
    :param config: configuration
    :return: joint PTN with unique edge ids
    """
    joint_ptn = SimpleDictGraph[Stop, Link]()
    rolling_edge_id = Counter()
    edge_maps: dict[str, dict[int, int]] = {}
    for modality, graph in ptns.items():
        edge_map: dict[int, int] = {}
        for node in graph.getNodes():
            joint_ptn.addNode(node)
        for edge in graph.getEdges():
            old_id = edge.getId()
            new_id = rolling_edge_id()
            edge.setId(new_id)
            edge_map[old_id] = new_id
            joint_ptn.addEdge(edge)
        edge_maps[modality] = edge_map
    PTNWriter.write(ptn=joint_ptn, config=config, modality=config.getStringValue('modality_joint_name'))
    return joint_ptn

def verify_od(ptn, od):
    """
    Verify that all nodes in the OD matrix exist in the (joint) PTN
    :param ptn: PTN to verify against
    :param od: OD matrix to verify
    """
    node_ids = [node.getId() for node in ptn.getNodes()]
    for pair in od.getODPairs():
        if not (pair.getOrigin() in node_ids):
            logger.error(f"Node {pair.getOrigin()} not found in the joint PTN, corresponding demand cannot be routed.")
        if not (pair.getDestination() in node_ids):
            logger.error(f"Node {pair.getDestination()} not found in the joint PTN, corresponding demand cannot be routed.")


def _remap_line_ids(
    ptns: dict[str, Graph[Stop, Link]],
    config: Config,
    all_modalities: list[str],
    scheduled_modalities: list[str],
) -> dict[str, dict[int, int]]:
    """
    Remap line ids to be unique between all modalities
    :param ptns: dictionary of PTNs for each modality
    :param config: configuration
    :param all_modalities: list of all modalities
    :param scheduled_modalities: list of scheduled modalities
    :return: dictionary of old to new line ids for each modality
    """
    joint_pool = LinePool()
    rolling_line_id = Counter()
    line_id_map: dict[str, dict[int, int]] = {}
    for modality in all_modalities:
        if not (modality in scheduled_modalities):
            continue
        pool = LineReader.read(
            ptn=ptns[modality],
            read_costs=False,
            config=config,
            modality=modality,
        )
        id_map = {}
        for line in pool.getLines():
            new_id = rolling_line_id()
            old_id = line.getId()
            id_map[old_id] = new_id
            line.line_id = new_id
            joint_pool.addLine(line)
        line_id_map[modality] = id_map
    LineWriter.write(
        pool=joint_pool,
        config=config,
        modality=config.getStringValue('modality_joint_name'),
    )
    return line_id_map

def write_modality_eans(joint_ean: EAN, config: Config, scheduled_modalities: list[str]) -> None:
    """
    Write EANs for each modality from the joint EAN
    :param joint_ean: joint EAN to split
    :param config: configuration
    :param scheduled_modalities: list of scheduled modalities
    :return: None
    """
    event_modality_filename = add_modality_prefix(config.getStringValue('modality_joint_name'), config.getStringValue('filename_event_modalities'))
    event_id_map, line_id_map = read_and_update_event_modalities(joint_ean, event_modality_filename)
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
            config=config,
            modality=modality,
        )

def run():
    logger.info('Started to create multimodal EAN')
    config = ConfigReader.read(sys.argv[-1])

    scheduled_modalities: list[str] = []
    nonscheduled_modalities: list[str] = []
    all_modalities = config.getStringListValue("modalities_all")
    for mode in all_modalities:
        category = config.getStringValue(f"{mode}.modality_category")
        if category == "line-based":
            scheduled_modalities.append(mode)
        elif category == "nonscheduled":
            nonscheduled_modalities.append(mode)
    if len(scheduled_modalities) == 0:
        logger.error("No scheduled modalities found, cannot create multimodal EAN")


    joint_modality_name = config.getStringValue('modality_joint_name')

    stops_filename = config.getStringValue('default_stops_file')
    edges_filename = config.getStringValue('default_edges_file')

    # remap edge ids to be unique between all modalities
    logger.info('Remapping edge ids')
    ptns: dict[str, Graph[Stop, Link]] = {}
    for modality in all_modalities:
        ptns[modality] = PTNReader.read(
            stop_file_name=add_modality_prefix(modality, stops_filename),
            link_file_name=add_modality_prefix(modality, edges_filename),
            modality=modality,
        )

    joint_ptn = _remap_edge_ids(ptns=ptns, config=config)
    distribute_passengers = not config.getBooleanValue('ean_construction_skip_passenger_distribution')
    if distribute_passengers:
        od_file = add_modality_prefix(joint_modality_name, config.getStringValue('default_od_file'))
        if os.path.isfile(od_file):
            od: MapOD = ODReader.read(None, config=config, modality=joint_modality_name)
        else:
            logger.info(f'Copying Infrastructure-OD.giv to {od_file}')
            od: MapOD = ODReader.readInfrastructureOd(None, config=config)
            ODWriter.write(ptn=joint_ptn, od=od, config=config, modality=joint_modality_name)
        verify_od(ptn=joint_ptn, od=od)

    # remap line ids to be unique between all modalities
    logger.info('Remapping line ids')
    line_id_map = _remap_line_ids(ptns=ptns, config=config, all_modalities=all_modalities, scheduled_modalities=scheduled_modalities)

    # Create joint EAN
    logger.info('Join EANs')
    eans: dict[str, EAN] = {}
    timetables: dict[str, PeriodicTimetable | None] = {}
    events_filename = config.getStringValue('default_events_periodic_file')
    activities_filename = config.getStringValue('default_activities_periodic_file')
    timetable_filename = config.getStringValue('default_timetable_periodic_file')
    min_change_time = config.getIntegerValue('ean_default_minimal_change_time')
    max_change_time = config.getIntegerValue('ean_default_maximal_change_time')
    period = config.getIntegerValue('period_length')
    units_per_minute = config.getIntegerValue('time_units_per_minute')
    transfer_penalty = config.getDoubleValue('ean_intermodal_change_penalty')
    penalty_algorithm = config.getStringValue('multimodal_transfer_penalty_algorithm')
    event_modality_filename = config.getStringValue('filename_event_modalities')
    fixed_timetable_modality = config.getStringListValue('fixed_timetable_modalities')
    use_timetable = config.getBooleanValue("ean_multimodal_distribute_passengers_with_timetable")

    have_timetable = True

    for modality in scheduled_modalities:
        mode_has_timetable=os.path.isfile(add_modality_prefix(modality, timetable_filename))
        ean, timetable = PeriodicEANReader.read(
            config=config,
            read_timetable=mode_has_timetable,
            modality=modality,
        )
        eans[modality] = ean
        timetables[modality] = timetable
        have_timetable = have_timetable and mode_has_timetable
    if use_timetable and not have_timetable:
        use_timetable = False
        logger.info('Not all EANs did have timetables. Passengers will be routed with lower bounds.')
    joiner = EANJoiner(
        eans=eans,
        timetables=timetables,
        min_change_time=min_change_time,
        max_change_time=max_change_time,
        period=period,
        units_per_minute=units_per_minute,
        line_id_map=line_id_map,
        config=config,
    )
    joiner.join_eans()

    joint_ean = joiner.joint_ean

    if len(fixed_timetable_modality) > 0:
        logger.info('Writing fixed timetable')
        for mode in fixed_timetable_modality:
            if not (mode in scheduled_modalities):
                logger.error(f'Modality {mode} is not a scheduled modality, cannot fix timetable.')
            if timetables[mode] is None:
                logger.error(f'Modality {mode} does not have a timetable, cannot fix timetable.')
        fixed_nodes = [
            node for node in joint_ean.getNodes()
            if node.modality in fixed_timetable_modality
        ]
        write_fixed_timetable(fixed_nodes, config=config)

    if distribute_passengers:
        ean_with_nonscheduled = copy_ean(joint_ean)

        if len(nonscheduled_modalities) > 0:
            nonscheduled_ptns: dict[str, Graph[Stop, Link]] = {}

            logger.info('Reading nonscheduled PTNs')
            for mode in nonscheduled_modalities:
                undir_ptn = PTNReader.read(config=config, modality=mode)
                nonscheduled_ptns[mode] = makeDirected(undir_ptn)

            logger.info('Creating joint scheduled-nonscheduled EAN')

            nonscheduled_ptn = buildMTGraph(graphs=nonscheduled_ptns.values(), penalty_algorithm=penalty_algorithm, transfer_penalty_rate=transfer_penalty)


            penalty_per_activity, artificial_nodes = join_nonscheduled_mode(ean_with_nonscheduled, nonscheduled_ptn, od, config)

        if use_timetable:
            logger.info('Distributing with timetable')
            distribute_passengers_penalized(ean_with_nonscheduled, od, config, useTimetable=True)
        else:
            logger.info('Distributing without timetable')
            distribute_passengers_penalized(ean_with_nonscheduled, od, config, useTimetable=False)

        # For the scheduled nodes (the nodes in joint_ean), set passenger counts based on routing with nonscheduled modalities included
        for event in joint_ean.getNodes():
            event.number_of_passengers = ean_with_nonscheduled.getNode(event.getId()).getNumberOfPassengers()
        for activity in joint_ean.getEdges():
            activity.setNumberOfPassengers(ean_with_nonscheduled.getEdge(activity.getId()).getNumberOfPassengers())

    logger.info('Writing joint EAN')
    PeriodicEANWriter.write(
        ean=joint_ean,
        timetable=joiner.joint_timetable,
        write_timetable=have_timetable,
        config=config,
        modality=joint_modality_name,
    )

    if distribute_passengers:
        if len(nonscheduled_modalities) > 0:
            write_nonscheduled_transfers(ean_with_nonscheduled, penalty_per_activity, artificial_nodes, config)

        logger.info('Writing modality EANs')
        # Passengers have been distributed in the joint EAN with nonscheduled transfers,
        # write the modality EANs with new passenger numbers
        write_modality_eans(joint_ean, config, scheduled_modalities)

    logger.info('Finished joining EANs')


if __name__ == '__main__':
    run()
