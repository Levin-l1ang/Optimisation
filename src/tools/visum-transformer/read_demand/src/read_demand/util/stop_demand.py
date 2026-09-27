from common.model.net import Net
from common.util.constants import OD_SECTION_HEADER, OD_ORIGIN_HEADER, OD_DESTINATION_HEADER, OD_VALUE_HEADER
from core.model.graph import Graph
from core.model.impl.dictOD import DictOD
from core.model.od import OD
from core.model.ptn import Link, Stop


def read_demand(net: Net, ptn: Graph[Stop, Link]) -> OD:
    od = DictOD()
    od_section = net.get_section(OD_SECTION_HEADER)
    for row in od_section.get_rows():
        origin_id = ptn.get_node_by_function(Stop.getShortName, od_section.get_entry_from_row(row, OD_ORIGIN_HEADER)).getId()
        destination_id = ptn.get_node_by_function(Stop.getShortName, od_section.get_entry_from_row(row, OD_DESTINATION_HEADER)).getId()
        if origin_id == destination_id:
            continue
        value = float(od_section.get_entry_from_row(row, OD_VALUE_HEADER))
        od.setValue(origin_id, destination_id, value)
    return od
