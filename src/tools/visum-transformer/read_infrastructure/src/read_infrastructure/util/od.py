from typing import Dict

from core.model.impl.dictOD import DictOD
from core.model.infrastructure import InfrastructureNode
from core.model.od import OD
from common.model.net import Net
from common.util.constants import OD_SECTION_HEADER, OD_ORIGIN_HEADER, OD_DESTINATION_HEADER, OD_VALUE_HEADER


def transform_od(od_data: Net, node_by_name: Dict[str, InfrastructureNode]) -> OD:
    od = DictOD()
    od_section = od_data.get_section(OD_SECTION_HEADER)
    for row in od_section.get_rows():
        origin_id = node_by_name[od_section.get_entry_from_row(row, OD_ORIGIN_HEADER)].getId()
        destination_id = node_by_name[od_section.get_entry_from_row(row, OD_DESTINATION_HEADER)].getId()
        value = float(od_section.get_entry_from_row(row, OD_VALUE_HEADER))
        od.setValue(origin_id, destination_id, value)
    return od