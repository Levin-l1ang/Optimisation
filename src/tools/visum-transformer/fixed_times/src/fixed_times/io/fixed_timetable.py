from typing import List

from core.io.csv import CsvWriter
from core.model.graph import Graph
from core.model.periodic_ean import PeriodicActivity, PeriodicEvent
from core.util.config import Config


def fixed_event_output(event: PeriodicEvent) -> List[str]:
    return [str(event.getId()), str(event.getTime()), str(event.getTime())]


def write_fixed_timetable(fixed_ean: Graph[PeriodicEvent, PeriodicActivity], fixed_timetable_file: str = "",
                          fixed_timetable_header: str = "", config: Config = Config.getDefaultConfig()):
    if not fixed_timetable_file:
        fixed_timetable_file = config.getStringValue("filename_tim_fixed_times")
    if not fixed_timetable_header:
        fixed_timetable_header = config.getStringValue("timetable_header_periodic_fixed")
    CsvWriter.writeListStatic(fixed_timetable_file, fixed_ean.getNodes(), lambda e: fixed_event_output(e),
                              PeriodicEvent.getId, fixed_timetable_header)