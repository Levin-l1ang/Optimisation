import logging
from typing import Dict

from common.model.net import Net
from common.util.constants import OD_SECTION_HEADER, OD_ORIGIN_HEADER, OD_DESTINATION_HEADER, OD_VALUE_HEADER
from core.exceptions.exceptions import LinTimException
from core.model.impl.dictOD import DictOD
from core.model.infrastructure import InfrastructureNode
from core.model.od import OD


def read_demand(net: Net, node_by_name: Dict[int, InfrastructureNode]) -> OD:
    od = DictOD()
    od_section = net.get_section(OD_SECTION_HEADER)
    # First, determine the header for the demand
    logger = logging.getLogger(__name__)
    if len(od_section.header) > 3:
        logger.warning("Demand file has more than three columns, its not clear which column will be chosen as "
                       "the demand column!")
    demand_index = -1
    sum_of_demand = 0
    for index, header in enumerate(od_section.header):
        if header != OD_ORIGIN_HEADER and header != OD_DESTINATION_HEADER:
            demand_index = index
            break
    if demand_index == -1:
        raise LinTimException("Unable to find demand header, abort!")
    for row in od_section.get_rows():
        origin_id = node_by_name[int(float(od_section.get_entry_from_row(row, OD_ORIGIN_HEADER)))].getId()
        destination_id = node_by_name[int(float(od_section.get_entry_from_row(row, OD_DESTINATION_HEADER)))].getId()
        value = float(row[demand_index])
        if value == 0:
            continue
        if origin_id == destination_id:
            logger.debug(f"Found demand inside of {origin_id}, skip")
            continue
        od.setValue(origin_id, destination_id, value)
        sum_of_demand += value
    logger.debug(f"Read {sum_of_demand} demand")
    return od