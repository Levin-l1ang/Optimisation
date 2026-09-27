import logging

from core.model.lines import LinePool
from core.util.change_and_go_method import build_cg_graph
from direct_travellers_IP import direct_travellers_IP
from helper import transform_ptn_into_networkx, transform_cg_into_networkx, sublist_iter
from single_source_all_shortest_paths import DijkstraAllShortestPaths
from single_source_shortest_paths import single_source_shortest_path_length

logger = logging.getLogger(__name__)


def extended_ptn_evaluation(ptn, od, lines, link_frequencies, statistic, parameters):
    get_capacitated_direct_travellers_ptn(ptn, od, lines, link_frequencies, statistic, parameters)


def get_capacitated_direct_travellers_ptn(
        ptn, od, lines: LinePool, link_frequencies, statistic, parameters):
    logger.debug("Precomputing preferable lines for capacitated direct travelers")
    ptn_nx = transform_ptn_into_networkx(ptn, parameters.drive_weight_function,
                                         parameters.wait_time,
                                         link_frequencies)

    edge_to_shortest_paths = {edge: {} for edge in ptn.getEdges()}
    preferable_line_ids = {}
    line_concept = lines.getLineConcept()

    cgn = build_cg_graph(
        line_concept=line_concept,
        model_drive=parameters.ean_model_weight_drive,
        model_wait=parameters.ean_model_weight_wait,
        model_change="MINIMAL_CHANGING_TIME",
        change_penalty=0,
        min_change_time=parameters.period_length,
        min_wait_time=parameters.min_wait_time,
        max_wait_time=parameters.max_wait_time,
        period_length=parameters.period_length,
        create_transfer_arcs=False,
        directed=ptn.isDirected()
    )
    cgn_nx = transform_cg_into_networkx(cgn)
    od_nodes = {}
    for cgnode in cgn.getNodes():
        if cgnode.getLineId() == 0:
            od_nodes[cgnode.getStopId()] = cgnode

    line_from_id = {line.getId(): line for line in line_concept}
    ptn_path_difference = parameters.wait_time
    cgn_path_difference = parameters.period_length

    for origin in ptn.getNodes():
        if origin.getId() not in od_nodes:
            # no demand starting at origin
            continue
        sp_dist_ptn = single_source_shortest_path_length(G=ptn_nx, source=origin)
        od_origin = od_nodes[origin.getId()]
        dijkstra_cgn = DijkstraAllShortestPaths(graph=cgn_nx, source=od_origin,
                                                weight="weight")
        for destination in ptn.getNodes():
            if od.getValue(origin.getId(), destination.getId()) == 0:
                continue
            preferable_line_ids[(origin.getId(), destination.getId())] = []
            od_destination = od_nodes[destination.getId()]
            l_ptn = sp_dist_ptn.get(destination, float("inf")) - ptn_path_difference
            l_cgn = dijkstra_cgn.dist.get(od_destination, float("inf")) - cgn_path_difference
            direct = (l_ptn == l_cgn)
            if not direct:
                continue
            shortest_paths = dijkstra_cgn.getPaths(od_destination)
            for sp_cgn in shortest_paths:
                line = line_from_id[sp_cgn[1].getLineId()]
                line_stop_ids = [n.getId() for n in line.getLinePath().getNodes()]
                sp_cgn_stop_ids = [n.getStopId() for n in sp_cgn[1:-1]]
                start, end = sublist_iter(line_stop_ids, sp_cgn_stop_ids)
                assert start is not None
                preferable_line_ids[(origin.getId(), destination.getId())].append(line.getId())
                sub_path = line.getLinePath().getEdges()[start: end]
                for edge in sub_path:
                    if line.getId() not in edge_to_shortest_paths[edge]:
                        edge_to_shortest_paths[edge][line.getId()] = []

                    edge_to_shortest_paths[edge][line.getId()].append((origin.getId(), destination.getId()))

    logger.info("Solve ip")
    no_of_direct_travellers_capacity = direct_travellers_IP(
        ptn, lines, od, edge_to_shortest_paths, preferable_line_ids,
        parameters)

    statistic.setValue("lc_obj_direct_travelers", no_of_direct_travellers_capacity)