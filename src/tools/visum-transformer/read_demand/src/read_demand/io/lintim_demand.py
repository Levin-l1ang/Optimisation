from typing import List

from core.io.csv import CsvWriter
from read_demand.model.demand import Demand
from read_demand.model.zone import Zone, ZonePath


def write_demand_zones(zones: List[Zone], filename: str, header: str):
    CsvWriter.writeListStatic(filename,
                              zones,
                              lambda z: [str(z.zone_id), str(z.x_coordinate), str(z.y_coordinate)],
                              lambda z: z.zone_id,
                              header)


def write_demand_paths(paths: List[ZonePath], filename: str, header: str):
    CsvWriter.writeListStatic(filename,
                              paths,
                              lambda p: [str(p.link_id), str(p.from_zone_id), str(p.to_stop_id),
                                         str(p.length), str(p.duration)],
                              lambda p: p.link_id,
                              header)


def write_demand(demand: List[Demand], filename: str, header: str):
    CsvWriter.writeListStatic(filename,
                              demand,
                              lambda d: [str(d.from_zone_id), str(d.to_zone_id), str(d.passengers)],
                              lambda d: d.from_zone_id,
                              header)