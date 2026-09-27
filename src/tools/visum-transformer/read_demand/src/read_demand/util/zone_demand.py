import logging
from typing import Dict, List

from common.model.net import Net
from common.util.constants import DEMAND_SECTION_HEADER, DEMAND_FROM_ZONE_HEADER, DEMAND_TO_ZONE_HEADER, \
    DEMAND_VALUE_HEADER
from read_demand.model.demand import Demand
from read_demand.model.zone import Zone

logger = logging.getLogger(__name__)


def get_demand(demand_net: Net, zones: Dict[int, Zone]) -> List[Demand]:
    demand_section = demand_net.get_section(DEMAND_SECTION_HEADER)
    demands = []
    for row in demand_section.get_rows():
        from_zone_id = int(demand_section.get_entry_from_row(row, DEMAND_FROM_ZONE_HEADER))
        to_zone_id = int(demand_section.get_entry_from_row(row, DEMAND_TO_ZONE_HEADER))
        if from_zone_id == to_zone_id:
            continue
        if from_zone_id not in zones or to_zone_id not in zones:
            logger.warning(f"Could not find both {from_zone_id} and {to_zone_id} in the list of read zones!")
        value = float(demand_section.get_entry_from_row(row, DEMAND_VALUE_HEADER))
        demands.append(Demand(from_zone_id, to_zone_id, value))
    return demands